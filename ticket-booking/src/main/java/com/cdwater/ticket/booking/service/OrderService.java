package com.cdwater.ticket.booking.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cdwater.ticket.booking.booking.BookingStrategy;
import com.cdwater.ticket.booking.dto.CreateOrderRequest;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.mapper.PaymentMapper;
import com.cdwater.ticket.booking.outbox.OutboxService;
import com.cdwater.ticket.booking.vo.CreateOrderVO;
import com.cdwater.ticket.booking.vo.OrderVO;
import com.cdwater.ticket.booking.vo.SeatPositionVO;
import com.cdwater.ticket.common.constant.EventMode;
import com.cdwater.ticket.common.constant.OrderStatus;
import com.cdwater.ticket.common.constant.PayStatus;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.EventStock;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.entity.Payment;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.common.result.PageResult;
import com.cdwater.ticket.event.mapper.EventStockMapper;
import com.cdwater.ticket.event.service.EventService;
import com.cdwater.ticket.event.vo.EventMetaVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final EventStockMapper eventStockMapper;
    private final OutboxService outboxService;
    private final EventService eventService;
    private final OrderCloseService orderCloseService;
    private final List<BookingStrategy> strategies;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redisson;

    @Value("${ticket.order.expire-minutes}")
    private long expireMinutes;

    @Value("${ticket.rate.order.limit}")
    private long rateLimit;

    @Value("${ticket.rate.order.period}")
    private long ratePeriod;

    public CreateOrderVO create(long userId, CreateOrderRequest request) {
        EventMetaVO event = eventService.getEvent(request.getEventId());
        acquireToken(userId);

        BookingStrategy strategy = orderCloseService.strategyOf(event);
        long orderNo = IdWorker.getId();
        Long token = strategy.acquire(userId, event, request.getSeatId(), orderNo);
        try {
            return transactionTemplate.execute(status -> persist(userId, event, request, orderNo));
        } catch (RuntimeException e) {
            // DB 兜底失败：Redis 上预扣的资源必须归还，否则永久少卖一张票。
            // 不能用 DB 库存覆写 Redis —— 并发下会把别的请求已扣掉的量冲回去。
            orderCloseService.compensateQuietly(event, token);
            throw e;
        }
    }

    private CreateOrderVO persist(long userId, EventMetaVO event, CreateOrderRequest request, long orderNo) {
        if (event.getMode() == EventMode.TICKET) {
            // 第二层防超卖：Redis 放行不等于 DB 一定扣得到
            int rows = eventStockMapper.update(null, Wrappers.<EventStock>lambdaUpdate()
                    .eq(EventStock::getEventId, event.getId())
                    .gt(EventStock::getStock, 0)
                    .setSql("stock = stock - 1"));
            if (rows == 0) {
                throw new BizException(ResultCode.CONFLICT.getCode(), "票已售罄");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(expireMinutes);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setEventId(event.getId());
        order.setSeatId(event.getMode() == EventMode.SEAT ? request.getSeatId() : null);
        order.setAmount(event.getPrice());
        order.setStatus(OrderStatus.UNPAID);
        order.setExpireTime(expireTime);
        order.setCreateTime(now);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 撞 uk_user_event_status：Redis 被清过或并发下 Lua 未拦住
            throw new BizException(ResultCode.CONFLICT.getCode(), "您已下过该活动的订单");
        }

        Payment payment = new Payment();
        payment.setOutTradeNo(IdWorker.getId());
        payment.setOrderId(order.getId());
        payment.setAmount(event.getPrice());
        payment.setStatus(PayStatus.UNPAID);
        payment.setExpireTime(expireTime);
        paymentMapper.insert(payment);

        // 与订单同事务落库，宕机也不会出现「订单已建但关单消息丢了」
        outboxService.enqueue(outboxService.orderTimeoutTopic(), String.valueOf(orderNo),
                payload(orderNo), expireTime);

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrderNo(orderNo);
        vo.setAmount(event.getPrice());
        vo.setExpireTime(expireTime);
        vo.setEventName(event.getName());
        vo.setEventAddress(event.getAddress());
        if (order.getSeatId() != null) {
            EventSeat seat = eventService.findSeat(event.getId(), order.getSeatId());
            if (seat != null) {
                vo.setSeat(new SeatPositionVO(seat.getRowNo(), seat.getColNo()));
            }
        }
        return vo;
    }

    public PageResult<OrderVO> list(long userId, String orderNo, Integer status, long page, long size) {
        Page<Order> result = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getUserId, userId)
                        .eq(orderNo != null && !orderNo.isBlank(), Order::getOrderNo, orderNo)
                        .eq(status != null, Order::getStatus, status)
                        .orderByDesc(Order::getCreateTime));

        Map<Long, EventMetaVO> events = new HashMap<>();
        List<Long> seatIds = result.getRecords().stream()
                .map(Order::getSeatId).filter(Objects::nonNull).distinct().toList();
        Map<Long, EventSeat> seats = new HashMap<>();
        if (!seatIds.isEmpty()) {
            eventService.findSeats(seatIds).forEach(seat -> seats.put(seat.getId(), seat));
        }

        List<OrderVO> records = new ArrayList<>(result.getRecords().size());
        for (Order order : result.getRecords()) {
            EventMetaVO event = events.computeIfAbsent(order.getEventId(), eventService::getEvent);
            OrderVO vo = new OrderVO();
            vo.setOrderNo(order.getOrderNo());
            vo.setEventId(order.getEventId());
            vo.setEventName(event.getName());
            vo.setEventAddress(event.getAddress());
            vo.setEventPrice(event.getPrice());
            vo.setAmount(order.getAmount());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            vo.setExpireTime(order.getExpireTime());
            vo.setPayTime(order.getPayTime());
            EventSeat seat = order.getSeatId() == null ? null : seats.get(order.getSeatId());
            if (seat != null) {
                vo.setSeat(new SeatPositionVO(seat.getRowNo(), seat.getColNo()));
            }
            records.add(vo);
        }

        Page<OrderVO> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(records);
        return PageResult.of(mapped);
    }

    public void cancel(long userId, String orderNo) {
        Order order = findByOrderNo(Long.parseLong(orderNo));
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (!orderCloseService.close(order)) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "订单已支付或已关闭");
        }
    }

    public Order findByOrderNo(long orderNo) {
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
    }

    /** 令牌桶把守秒杀入口：内部即 Redis Lua 原子实现 */
    private void acquireToken(long userId) {
        RRateLimiter limiter = redisson.getRateLimiter(RedisKey.orderRate(userId));
        limiter.trySetRate(RateType.OVERALL, rateLimit, ratePeriod, RateIntervalUnit.MILLISECONDS);
        if (!limiter.tryAcquire()) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS.getCode(), "操作过于频繁，请稍后重试");
        }
    }

    private String payload(long orderNo) {
        try {
            return objectMapper.writeValueAsString(Map.of("orderNo", String.valueOf(orderNo)));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "关单消息序列化失败");
        }
    }
}
