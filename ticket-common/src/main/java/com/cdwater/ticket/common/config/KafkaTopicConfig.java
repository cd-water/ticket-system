package com.cdwater.ticket.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 单节点 KRaft 副本数固定 1；显式建 topic，避免首条消息发送时才自动创建导致消费端错过 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderTimeoutTopic(@Value("${ticket.topic.order-timeout}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSmsTopic(@Value("${ticket.topic.payment-sms}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
