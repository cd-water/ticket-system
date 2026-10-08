package com.cdwater.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.auth.service.AuthService;
import com.cdwater.ticket.booking.mapper.OutboxMapper;
import com.cdwater.ticket.booking.outbox.OutboxRelay;
import com.cdwater.ticket.booking.outbox.OutboxService;
import com.cdwater.ticket.common.entity.Outbox;
import com.cdwater.ticket.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// 限流阈值压到 2 证明令牌桶在把守入口；中继 bean 默认被关，本类需要显式打开才能测 relay()
@TestPropertySource(properties = {"ticket.rate.order.limit=2", "ticket.outbox.enabled=true"})
class OutboxRateLimitTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private TestSupport testSupport;

    private String token;

    @BeforeEach
    void login() {
        testSupport.resetBookingState();
        token = authService.pwdLogin("13800000001", "Aa123456").getAccessToken();
    }

    @Test
    void 下单超过令牌桶配额返回C429() throws Exception {
        // 必须用不同活动：一人一单只限「同一活动重复下单」，同一活动第二次会是 C409 而非 A200
        for (long eventId : new long[]{3, 5}) {
            mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventId\":" + eventId + "}"))
                    .andExpect(jsonPath("$.code").value("A200"));
        }
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1}"))
                .andExpect(jsonPath("$.code").value("C429"));
    }

    @Test
    void 中继只投递到期消息并标记已投递() {
        outboxMapper.delete(Wrappers.<Outbox>lambdaQuery());
        outboxService.enqueue(outboxService.orderTimeoutTopic(), "1", "{\"orderNo\":\"1\"}",
                LocalDateTime.now().minusMinutes(5));
        outboxService.enqueue(outboxService.orderTimeoutTopic(), "2", "{\"orderNo\":\"2\"}",
                LocalDateTime.now().plusHours(1));

        outboxRelay.relay();

        List<Outbox> all = outboxMapper.selectList(Wrappers.<Outbox>lambdaQuery().orderByAsc(Outbox::getId));
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getStatus()).isEqualTo(1);
        assertThat(all.get(1).getStatus()).isZero();
    }
}
