package com.cdwater.ticket.booking.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.service.OrderCloseService;
import com.cdwater.ticket.common.constant.OrderStatus;
import com.cdwater.ticket.common.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兜底关单。Outbox + Kafka 负责按时关单，但中继投递失败、消费者异常或消息丢失时
 * 订单会永远卡在待支付 —— 用户从此再无法对同一活动下单（唯一键占位）。
 * 这里按 expire_time 扫一遍已过期未支付订单，走同一条 CAS，重复执行安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutSweeper {

    private static final int BATCH_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderCloseService orderCloseService;

    @Scheduled(fixedDelay = 30_000)
    public void sweep() {
        List<Order> expired = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.UNPAID)
                .le(Order::getExpireTime, LocalDateTime.now())
                .orderByAsc(Order::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (Order order : expired) {
            try {
                if (orderCloseService.close(order)) {
                    log.info("兜底关单 orderNo={}", order.getOrderNo());
                }
            } catch (Exception e) {
                // 单条失败不影响整批，下一轮重来
                log.warn("兜底关单失败 orderNo={}，下轮重试", order.getOrderNo(), e);
            }
        }
    }
}
