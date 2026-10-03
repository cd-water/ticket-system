package com.cdwater.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock 接口冒烟验证：逐条核对 docs/API.md 的响应结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MockApiSmokeTest {

    @Autowired
    private MockMvc mvc;

    /** JsonPath 对小数字返回 Integer、大数字返回 Long，统一按 Number 取值 */
    private static long num(String json, String path) {
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, path)).longValue();
    }

    @Test
    void 活动列表按模式与关键词过滤() throws Exception {
        mvc.perform(get("/api/events").param("mode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("Bilibili World 2023（BW2023）"))
                .andExpect(jsonPath("$.data.records[0].price").value(98.00));

        mvc.perform(get("/api/events").param("mode", "2").param("keyword", "周杰伦"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(2));

        mvc.perform(get("/api/events").param("mode", "1").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }

    @Test
    void 抢票详情与选座详情() throws Exception {
        mvc.perform(get("/api/events/1/ticket"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.stock").value(1000));

        // 4 排 × 10 座 = 40 个座位，按行优先；seatId 2 已售
        mvc.perform(get("/api/events/2/seat"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.rowCount").value(4))
                .andExpect(jsonPath("$.data.colCount").value(10))
                .andExpect(jsonPath("$.data.seats.length()").value(40))
                .andExpect(jsonPath("$.data.seats[0].rowNo").value(1))
                .andExpect(jsonPath("$.data.seats[0].colNo").value(1))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(1))
                .andExpect(jsonPath("$.data.seats[1].status").value(1))
                .andExpect(jsonPath("$.data.seats[10].rowNo").value(2))
                .andExpect(jsonPath("$.data.seats[10].seatId").value(11));
    }

    @Test
    void 模式不匹配与活动不存在() throws Exception {
        mvc.perform(get("/api/events/1/seat"))
                .andExpect(jsonPath("$.code").value("C400"));
        mvc.perform(get("/api/events/2/ticket"))
                .andExpect(jsonPath("$.code").value("C400"));
        mvc.perform(get("/api/events/99/ticket"))
                .andExpect(jsonPath("$.code").value("C404"));
        mvc.perform(get("/api/events").param("mode", "1").param("keyword", "不存在"))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void 认证接口() throws Exception {
        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\",\"code\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userInfo.phone").value("13800000001"));

        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\",\"code\":\"000000\"}"))
                .andExpect(jsonPath("$.code").value("C400"));

        mvc.perform(post("/api/auth/pwd-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\",\"password\":\"Aa123456\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.userInfo.id").value(1));

        mvc.perform(post("/api/auth/pwd-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value("C400"));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"any\"}"))
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(post("/api/auth/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000001\"}"))
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"any\"}"))
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Ab1234567\"}"))
                .andExpect(jsonPath("$.code").value("A200"));
    }

    @Test
    void 下单占用座位() throws Exception {
        String before = mvc.perform(get("/api/orders"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andReturn().getResponse().getContentAsString();
        long totalBefore = num(before, "$.data.total");

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"seatId\":7}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.orderNo").isString())
                .andExpect(jsonPath("$.data.amount").value(600.00))
                .andExpect(jsonPath("$.data.eventName").value("周杰伦2023嘉年华世界巡回演唱会-上海站"))
                .andExpect(jsonPath("$.data.seat.rowNo").value(1))
                .andExpect(jsonPath("$.data.seat.colNo").value(7))
                .andExpect(jsonPath("$.data.expireTime").isNotEmpty());

        // 订单确实落进了内存列表
        mvc.perform(get("/api/orders"))
                .andExpect(jsonPath("$.data.total").value(totalBefore + 1))
                .andExpect(jsonPath("$.data.records[0].status").value(0));
    }

    @Test
    void 下单校验与座位冲突() throws Exception {
        // 自己先占一个座，再验证第二次下单被拒——不依赖其他测试的执行顺序
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"seatId\":9}"))
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"seatId\":9}"))
                .andExpect(jsonPath("$.code").value("C409"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2}"))
                .andExpect(jsonPath("$.code").value("C400"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"seatId\":999}"))
                .andExpect(jsonPath("$.code").value("C400"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":99}"))
                .andExpect(jsonPath("$.code").value("C404"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value("C400"));
    }

    @Test
    void 抢票模式下单不要求座位() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.amount").value(98.00))
                .andExpect(jsonPath("$.data.seat").doesNotExist());
    }

    @Test
    void 支付后订单转为已支付且不可重复支付() throws Exception {
        String body = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":3}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andReturn().getResponse().getContentAsString();
        String orderNo = com.jayway.jsonpath.JsonPath.read(body, "$.data.orderNo");

        mvc.perform(post("/api/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mvc.perform(post("/api/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value("C409"));

        mvc.perform(post("/api/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"1\"}"))
                .andExpect(jsonPath("$.code").value("C404"));
    }

    @Test
    void 订单按订单号与状态精确查询() throws Exception {
        mvc.perform(get("/api/orders").param("status", "2"))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(2));

        mvc.perform(get("/api/orders").param("orderNo", "7845129365720192513"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("7845129365720192513"));

        mvc.perform(get("/api/orders").param("orderNo", "1"))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
