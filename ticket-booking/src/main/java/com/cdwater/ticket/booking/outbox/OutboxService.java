package com.cdwater.ticket.booking.outbox;

import com.cdwater.ticket.booking.mapper.OutboxMapper;
import com.cdwater.ticket.common.entity.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 与业务写同一事务，宕机也不会丢消息；实际投递由 OutboxRelay 负责 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMapper outboxMapper;

    @Value("${ticket.topic.order-timeout}")
    private String orderTimeoutTopic;

    @Value("${ticket.topic.payment-sms}")
    private String paymentSmsTopic;

    public String orderTimeoutTopic() {
        return orderTimeoutTopic;
    }

    public String paymentSmsTopic() {
        return paymentSmsTopic;
    }

    public void enqueue(String topic, String messageKey, String payload, LocalDateTime deliverTime) {
        Outbox message = new Outbox();
        message.setTopic(topic);
        message.setMessageKey(messageKey);
        message.setPayload(payload);
        message.setDeliverTime(deliverTime);
        message.setStatus(0);
        outboxMapper.insert(message);
    }
}
