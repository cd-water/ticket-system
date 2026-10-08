package com.cdwater.ticket;

import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.support.TestSupport;
import org.junit.jupiter.api.Test;
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
 * 生产默认配置（ticket.sms.echo-code=false）下验证码必须照常生成与入库 ——
 * echo 开关只该控制「是否回传给前端」，不该顺手把关卡也关掉。
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
}
