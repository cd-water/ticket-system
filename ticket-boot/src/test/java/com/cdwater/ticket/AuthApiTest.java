package com.cdwater.ticket;

import com.cdwater.ticket.auth.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    private static String str(String json, String path) {
        return JsonPath.read(json, path);
    }

    private String loginRaw(String phone, String password) throws Exception {
        return mvc.perform(post("/api/auth/pwd-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andReturn().getResponse().getContentAsString();
    }

    private String accessToken(String phone, String password) throws Exception {
        return str(loginRaw(phone, password), "$.data.accessToken");
    }

    @Test
    void 密码登录成功与失败() throws Exception {
        String token = accessToken("13800000001", "Aa123456");
        assertThat(token).startsWith("eyJ");

        mvc.perform(post("/api/auth/pwd-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value("C400"));

        // 13800000002 是无密码的短信账号，密码登录必须被拒
        mvc.perform(post("/api/auth/pwd-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000002\",\"password\":\"Aa123456\"}"))
                .andExpect(jsonPath("$.code").value("C400"));
    }

    @Test
    void 短信登录自动注册且验证码一次性() throws Exception {
        String phone = "1390000" + (System.nanoTime() % 10000);
        String code = authService.sendCode(phone);
        assertThat(code).hasSize(6);

        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.userInfo.phone").value(phone));

        // 同一个码不能用第二次
        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.code").value("C400"));
    }

    @Test
    void 刷新令牌轮换后旧令牌失效() throws Exception {
        String first = loginRaw("13800000001", "Aa123456");
        String oldRefresh = str(first, "$.data.refreshToken");

        String refreshed = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andReturn().getResponse().getContentAsString();
        assertThat(str(refreshed, "$.data.refreshToken")).isNotEqualTo(oldRefresh);

        // 旧 refresh 已轮换失效
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(jsonPath("$.code").value("C401"));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(jsonPath("$.code").value("C401"));
    }

    @Test
    void 登出后刷新失效() throws Exception {
        String refresh = str(loginRaw("13800000001", "Aa123456"), "$.data.refreshToken");

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(jsonPath("$.code").value("C401"));
    }

    @Test
    void 修改密码使全部会话失效() throws Exception {
        String a = loginRaw("13800000001", "Aa123456");
        String b = loginRaw("13800000001", "Aa123456");
        String tokenA = str(a, "$.data.accessToken");

        mvc.perform(post("/api/auth/password").header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Ab1234567\"}"))
                .andExpect(jsonPath("$.code").value("A200"));

        for (String raw : new String[]{a, b}) {
            mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + str(raw, "$.data.refreshToken") + "\"}"))
                    .andExpect(jsonPath("$.code").value("C401"));
        }

        // 改回种子密码，避免污染后续任务
        String tokenAfter = accessToken("13800000001", "Ab1234567");
        mvc.perform(post("/api/auth/password").header("Authorization", "Bearer " + tokenAfter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Aa123456\"}"))
                .andExpect(jsonPath("$.code").value("A200"));
    }

    @Test
    void 未携带令牌时业务接口返回C401() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C401"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C401"));
    }

    @Test
    void 携带有效令牌时不再返回C401() throws Exception {
        String token = accessToken("13800000001", "Aa123456");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A200"));
    }

    @Test
    void 篡改令牌被拒() throws Exception {
        mvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.forged"))
                .andExpect(jsonPath("$.code").value("C401"));
    }

    @Test
    void 公开接口无需令牌() throws Exception {
        mvc.perform(get("/api/events").param("mode", "1"))
                .andExpect(jsonPath("$.code").value("A200"));
    }
}
