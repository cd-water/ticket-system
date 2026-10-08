package com.cdwater.ticket.booking.outbox;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.booking.mapper.OutboxMapper;
import com.cdwater.ticket.common.entity.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轮询 t_outbox 把到期消息投给 Kafka。status 保持 0 直到 broker 确认，
 * 因此宕机或发送失败都会在下一轮重投 —— 至少一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticket.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<Outbox> pending = outboxMapper.selectList(Wrappers.<Outbox>lambdaQuery()
                .eq(Outbox::getStatus, 0)
                .le(Outbox::getDeliverTime, LocalDateTime.now())
                .orderByAsc(Outbox::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (Outbox message : pending) {
            try {
                // 必须等 broker ack，否则「发到一半进程挂掉」会丢消息
                kafka.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(3, TimeUnit.SECONDS);
                outboxMapper.update(null, Wrappers.<Outbox>lambdaUpdate()
                        .eq(Outbox::getId, message.getId())
                        .eq(Outbox::getStatus, 0)
                        .set(Outbox::getStatus, 1));
            } catch (Exception e) {
                // 单条失败不影响整批，下一轮重试
                log.warn("outbox 投递失败 id={}，下轮重试", message.getId(), e);
            }
        }
    }
}
