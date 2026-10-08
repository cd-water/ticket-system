package com.cdwater.ticket.booking.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.booking.BookingStrategy;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.mapper.PaymentMapper;
import com.cdwater.ticket.common.constant.OrderStatus;
import com.cdwater.ticket.common.constant.PayStatus;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.entity.Payment;
import com.cdwater.ticket.event.service.EventService;
import com.cdwater.ticket.event.vo.EventMetaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 关单唯一入口。手动取消与超时消费者都走这里，
 * 靠同一条 CAS 保证「先到先得、后到者看到状态变化就退出」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCloseService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final EventService eventService;
    private final List<BookingStrategy> strategies;

    @Transactional(rollbackFor = Exception.class)
    public boolean close(Order order) {
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getOrderNo, order.getOrderNo())
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .set(Order::getStatus, OrderStatus.CANCELLED));
        if (rows == 0) {
            // 已被支付或已取消，本轮放弃；这同时构成了消费端的幂等保证
            return false;
        }
        paymentMapper.update(null, Wrappers.<Payment>lambdaUpdate()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, PayStatus.UNPAID)
                .set(Payment::getStatus, PayStatus.CLOSED));
        EventMetaVO event = eventService.getEvent(order.getEventId());
        strategyOf(event).release(event, order);
        return true;
    }

    /** 下单事务失败时归还 Redis 上已预扣的资源；此处不该再抛异常 */
    public void compensateQuietly(EventMetaVO event, Long token) {
        try {
            strategyOf(event).compensate(event, token);
        } catch (Exception e) {
            log.error("归还下单预扣资源失败 eventId={} token={}", event.getId(), token, e);
        }
    }

    public BookingStrategy strategyOf(EventMetaVO event) {
        return strategyOf(event.getMode());
    }

    private BookingStrategy strategyOf(int mode) {
        return strategies.stream()
                .filter(s -> s.mode() == mode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有匹配 mode=" + mode + " 的下单策略"));
    }
}
