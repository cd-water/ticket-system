package com.cdwater.ticket;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cdwater.ticket.auth.service.SmsCodeService;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 生产默认配置（ticket.sms.echo-code=false）下：
 * 验证码必须照常生成入库，且要有办法拿到它 —— 不回传响应体，就该落到控制台。
 * 否则无短信服务商时短信登录彻底不可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ticket.sms.echo-code=false")
class SmsCodeProductionModeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TestSupport testSupport;

    @Test
    void 关闭回传时验证码依然生成并可用于登录() throws Exception {
        String phone = "1397000-" + (System.nanoTime() % 1_000_000);
        testSupport.deleteUsersByPrefix("1397000");

        mvc.perform(post("/api/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A200"))
                // 不回传：响应里不能带验证码
                .andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.nullValue()));

        String stored = redis.opsForValue().get(RedisKey.smsCode(phone));
        assertThat(stored).as("关闭回传不等于不生成验证码").isNotNull();

        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + stored + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.userInfo.phone").value(phone));

        testSupport.deleteUsersByPrefix("1397000");
    }

    @Test
    void 关闭回传时验证码打印到控制台() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SmsCodeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String phone = "1397001-" + (System.nanoTime() % 1_000_000);
            mvc.perform(post("/api/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\"}"))
                    .andExpect(jsonPath("$.code").value("A200"));

            String stored = redis.opsForValue().get(RedisKey.smsCode(phone));
            assertThat(stored).isNotNull();
            assertThat(appender.list)
                    .as("不回传响应体时，验证码必须能在控制台看到，否则无短信服务商时无从获取")
                    .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains(stored));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
