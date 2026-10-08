package com.cdwater.ticket.booking.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.mapper.OrderMapper;
import com.cdwater.ticket.booking.service.OrderCloseService;
import com.cdwater.ticket.common.entity.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderMapper orderMapper;
    private final OrderCloseService orderCloseService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${ticket.topic.order-timeout}")
    public void onMessage(String payload, Acknowledgment ack) {
        try {
            long orderNo = objectMapper.readTree(payload).get("orderNo").asLong();
            Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                    .eq(Order::getOrderNo, orderNo));
            if (order == null) {
                ack.acknowledge();
                return;
            }
            // CAS 本身就是幂等保证：已支付或已取消时 close 返回 false，直接放行
            log.info("超时关单 orderNo={} closed={}", orderNo, orderCloseService.close(order));
            ack.acknowledge();
        } catch (Exception e) {
            // ponytail: 消费失败即记日志并 ack。关单逻辑幂等，重投无收益；毒消息无限重投会卡住分区。
            // 需要退避重试与死信队列时，改用 DefaultErrorHandler + DeadLetterPublishingRecoverer。
            log.error("超时关单消息处理失败 payload={}", payload, e);
            ack.acknowledge();
        }
    }
}
