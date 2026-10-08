package com.cdwater.ticket.booking.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 支付成功短信。Mock 实现只落日志，SETNX 保证同一笔订单只发一次。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSmsConsumer {

    private static final String SENT_PREFIX = "mq:sms:sent:";

    private final StringRedisTemplate redis;

    @KafkaListener(topics = "${ticket.topic.payment-sms}")
    public void onMessage(String key, String payload, Acknowledgment ack) {
        try {
            if (!Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(SENT_PREFIX + key, "1", Duration.ofDays(1)))) {
                ack.acknowledge();
                return;
            }
            log.info("[mock] 支付成功短信已发送 orderNo={} payload={}", key, payload);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("支付短信消费失败 payload={}", payload, e);
            ack.acknowledge();
        }
    }
}
