package com.cdwater.ticket.booking.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.mapper.PaymentMapper;
import com.cdwater.ticket.booking.outbox.OutboxService;
import com.cdwater.ticket.common.constant.OrderStatus;
import com.cdwater.ticket.common.constant.PayStatus;
import com.cdwater.ticket.common.entity.EventSeat;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.entity.Payment;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import com.cdwater.ticket.event.mapper.EventSeatMapper;
import com.cdwater.ticket.event.service.EventService;
import com.cdwater.ticket.event.vo.EventMetaVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付与超时关单共用同一条 CAS：先到者生效，后到者看到状态已变就退出。
 * 事务方法内不碰 Redis，资源固化在事务提交后由 confirmResources 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final EventSeatMapper eventSeatMapper;
    private final OutboxService outboxService;
    private final EventService eventService;
    private final OrderCloseService orderCloseService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public void pay(long userId, String orderNo) {
        long no = Long.parseLong(orderNo);
        boolean won = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> settle(userId, no, "wx-mock-" + IdWorker.getId())));
        if (!won) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "订单已支付或已关闭");
        }
        confirmResources(no);
    }

    /** 模拟微信异步回调；重复投递是常态而非错误，因此不再抛 C409 */
    public void handleCallback(String outTradeNo, String transactionId, boolean success) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOutTradeNo, Long.parseLong(outTradeNo)));
        if (payment == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "支付单不存在");
        }
        Order order = orderMapper.selectById(payment.getOrderId());

        if (!success) {
            paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                    .eq(Payment::getId, payment.getId())
                    .eq(Payment::getStatus, PayStatus.UNPAID)
                    .set(Payment::getStatus, PayStatus.FAILED));
            return;
        }

        boolean won = Boolean.TRUE.equals(
                transactionTemplate.execute(status -> settle(order.getUserId(), order.getOrderNo(), transactionId)));
        if (won) {
            confirmResources(order.getOrderNo());
        } else {
            log.info("支付回调重复或订单已终结 outTradeNo={}", outTradeNo);
        }
    }

    /** 事务内的支付落库。返回 false 表示订单已不是待支付，本次是竞态输家。 */
    private Boolean settle(long userId, long orderNo, String transactionId) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (order == null || order.getUserId() != userId) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .set(Order::getStatus, OrderStatus.PAID)
                .set(Order::getPayTime, now));
        if (rows == 0) {
            return false;
        }

        if (order.getSeatId() != null) {
            int seatRows = eventSeatMapper.update(null, Wrappers.<EventSeat>lambdaUpdate()
                    .eq(EventSeat::getId, order.getSeatId())
                    .eq(EventSeat::getStatus, 0)
                    .set(EventSeat::getStatus, 1));
            if (seatRows == 0) {
                // 座位已被别的订单售出，整笔支付回滚，调用方按输家处理
                throw new BizException(ResultCode.CONFLICT.getCode(), "座位已被售出");
            }
        }

        paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, PayStatus.UNPAID)
                .set(Payment::getStatus, PayStatus.SUCCESS)
                .set(Payment::getTransactionId, transactionId)
                .set(Payment::getPayTime, now));

        outboxService.enqueue(outboxService.paymentSmsTopic(), String.valueOf(orderNo),
                smsPayload(order, transactionId), now);
        return true;
    }

    /** 事务提交后再动 Redis：失败时 DB 已回滚，不会出现「订单已取消但座位已标记售出」 */
    private void confirmResources(long orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            return;
        }
        EventMetaVO event = eventService.getEvent(order.getEventId());
        orderCloseService.strategyOf(event).confirm(event, order);
    }

    private String smsPayload(Order order, String transactionId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "orderNo", String.valueOf(order.getOrderNo()),
                    "transactionId", transactionId,
                    "amount", order.getAmount().toPlainString()));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "短信消息序列化失败");
        }
    }
}
