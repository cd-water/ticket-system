package com.cdwater.ticket;

import com.cdwater.ticket.auth.service.AuthService;
import com.cdwater.ticket.booking.dto.CreateOrderRequest;
import com.cdwater.ticket.booking.service.OrderCloseService;
import com.cdwater.ticket.booking.service.OrderService;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.support.TestSupport;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
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
class OrderApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderCloseService orderCloseService;

    @Autowired
    private TestSupport testSupport;

    private long userId;
    private String token;

    @BeforeEach
    void login() {
        testSupport.resetBookingState();
        var vo = authService.pwdLogin("13800000001", "Aa123456");
        userId = vo.getUserInfo().getId();
        token = vo.getAccessToken();
    }

    private static String str(String json, String path) {
        return JsonPath.read(json, path);
    }

    /** JsonPath 对小数字返回 Integer，按 int 取值再比 */
    private static int num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).intValue();
    }

    private String createOrder(String content) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(jsonPath("$.code").value("A200"))
                .andReturn().getResponse().getContentAsString();
    }

    private void cancel(String orderNo) throws Exception {
        mvc.perform(post("/api/orders/cancel").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"));
    }

    @Test
    void 选座下单返回订单号与座位位置() throws Exception {
        String raw = createOrder("{\"eventId\":2,\"seatId\":7}");
        assertThat(str(raw, "$.data.orderNo")).isNotBlank();
        assertThat(num(raw, "$.data.seat.rowNo")).isEqualTo(1);
        assertThat(num(raw, "$.data.seat.colNo")).isEqualTo(7);
        assertThat(((Number) JsonPath.read(raw, "$.data.amount")).intValue()).isEqualTo(600);
        assertThat(str(raw, "$.data.eventName")).isEqualTo("周杰伦2023嘉年华世界巡回演唱会-上海站");
    }

    @Test
    void 抢票下单不要求座位且seat为null() throws Exception {
        String raw = createOrder("{\"eventId\":3}");
        assertThat(((Number) JsonPath.read(raw, "$.data.amount")).intValue()).isEqualTo(128);
        assertThat(str(raw, "$.data.seat")).isNull();
    }

    @Test
    void 参数与状态校验() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value("C400"));

        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":99}"))
                .andExpect(jsonPath("$.code").value("C404"));

        // 对抢票活动传座位 → 忽略
        createOrder("{\"eventId\":5,\"seatId\":1}");

        // 选座活动必须指定座位
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":4}"))
                .andExpect(jsonPath("$.code").value("C400"));

        // 座位号越界
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"seatId\":999}"))
                .andExpect(jsonPath("$.code").value("C400"));
    }

    @Test
    void 同一座位第二次下单被拒() throws Exception {
        createOrder("{\"eventId\":6,\"seatId\":241}");
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":6,\"seatId\":241}"))
                .andExpect(jsonPath("$.code").value("C409"));
    }

    @Test
    void 一人一单且取消后可再次下单() throws Exception {
        createOrder("{\"eventId\":5}");
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":5}"))
                .andExpect(jsonPath("$.code").value("C409"));

        String orderNo = str(createOrder("{\"eventId\":1}"), "$.data.orderNo");
        cancel(orderNo);
        // 唯一键含 status，取消后放行
        createOrder("{\"eventId\":1}");
    }

    @Test
    void 取消订单归还座位() throws Exception {
        String orderNo = str(createOrder("{\"eventId\":6,\"seatId\":251}"), "$.data.orderNo");
        cancel(orderNo);
        createOrder("{\"eventId\":6,\"seatId\":251}");
    }

    @Test
    void 取消不存在的订单与重复取消() throws Exception {
        mvc.perform(post("/api/orders/cancel").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"1\"}"))
                .andExpect(jsonPath("$.code").value("C404"));

        String orderNo = str(createOrder("{\"eventId\":4,\"seatId\":68}"), "$.data.orderNo");
        cancel(orderNo);
        mvc.perform(post("/api/orders/cancel").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value("C409"));
    }

    @Test
    void 取消后订单状态与支付单状态同步关闭() throws Exception {
        String orderNo = str(createOrder("{\"eventId\":3}"), "$.data.orderNo");
        cancel(orderNo);

        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("orderNo", orderNo))
                .andExpect(jsonPath("$.data.records[0].status").value(2));
    }

    @Test
    void 订单列表只返回本人订单并可按状态过滤() throws Exception {
        String orderNo = str(createOrder("{\"eventId\":4,\"seatId\":103}"), "$.data.orderNo");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("orderNo", orderNo))
                .andExpect(jsonPath("$.code").value("A200"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.records[0].status").value(0))
                .andExpect(jsonPath("$.data.records[0].eventPrice").value(380.00))
                .andExpect(jsonPath("$.data.records[0].seat.rowNo").value(4))
                .andExpect(jsonPath("$.data.records[0].seat.colNo").value(4))
                .andExpect(jsonPath("$.data.records[0].payTime").value(Matchers.nullValue()));

        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).param("status", "2"))
                .andExpect(jsonPath("$.data.records[*].status")
                        .value(Matchers.everyItem(Matchers.is(2))));
    }

    @Test
    void 超时关单走与手动取消相同的CAS() {
        long orderNo = orderService.create(userId, seatRequest(4, 103)).getOrderNo();
        Order order = orderService.findByOrderNo(orderNo);

        assertThat(orderCloseService.close(order)).isTrue();
        assertThat(orderService.findByOrderNo(orderNo).getStatus()).isEqualTo(2);
        // 第二次关单是幂等的，不再释放一次资源
        assertThat(orderCloseService.close(orderService.findByOrderNo(orderNo))).isFalse();
    }

    private CreateOrderRequest seatRequest(long eventId, long seatId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setEventId(eventId);
        request.setSeatId(seatId);
        return request;
    }
}
