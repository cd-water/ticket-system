package com.cdwater.ticket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.auth.service.AuthService;
import com.cdwater.ticket.booking.mapper.PaymentMapper;
import com.cdwater.ticket.booking.service.OrderService;
import com.cdwater.ticket.common.entity.Order;
import com.cdwater.ticket.common.entity.Payment;
import com.cdwater.ticket.support.TestSupport;
import com.jayway.jsonpath.JsonPath;
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
class PayApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private TestSupport testSupport;

    private String token;

    @BeforeEach
    void login() {
        testSupport.resetBookingState();
        token = authService.pwdLogin("13800000001", "Aa123456").getAccessToken();
    }

    private String createOrder(String content) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(jsonPath("$.code").value("A200"))
                .andReturn().getResponse().getContentAsString();
    }

    private Payment paymentOf(long orderNo) {
        Order order = orderService.findByOrderNo(orderNo);
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, order.getId()));
    }

    @Test
    void 支付成功后订单转已支付且重复支付被拒() throws Exception {
        String orderNo = JsonPath.read(createOrder("{\"eventId\":3}"), "$.data.orderNo");

        mvc.perform(post("/api/pay").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("orderNo", orderNo))
                .andExpect(jsonPath("$.data.records[0].status").value(1))
                .andExpect(jsonPath("$.data.records[0].payTime").isNotEmpty());

        mvc.perform(post("/api/pay").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value("C409"));

        mvc.perform(post("/api/pay").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"1\"}"))
                .andExpect(jsonPath("$.code").value("C404"));

        mvc.perform(post("/api/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("C401"));
    }

    @Test
    void 支付回调无需鉴权且重复投递返回成功() throws Exception {
        String orderNo = JsonPath.read(createOrder("{\"eventId\":3}"), "$.data.orderNo");
        Payment payment = paymentOf(Long.parseLong(orderNo));
        String body = "{\"outTradeNo\":\"" + payment.getOutTradeNo()
                + "\",\"transactionId\":\"wx-contract-1\",\"success\":true}";

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/pay/callback").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("A200"));
        }

        assertThat(orderService.findByOrderNo(Long.parseLong(orderNo)).getStatus()).isEqualTo(1);
    }

    @Test
    void 支付回调支付单不存在返回C404() throws Exception {
        mvc.perform(post("/api/pay/callback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"1\",\"transactionId\":\"wx-x\",\"success\":true}"))
                .andExpect(jsonPath("$.code").value("C404"));
    }

    @Test
    void 支付选座订单后座位转为已售() throws Exception {
        // 活动 6 的 seatId 区间是 191~270，191 是首个座位且 seed 中待售
        String orderNo = JsonPath.read(createOrder("{\"eventId\":6,\"seatId\":191}"), "$.data.orderNo");

        mvc.perform(post("/api/pay").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(jsonPath("$.code").value("A200"));

        mvc.perform(get("/api/events/6/seat"))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(191))
                .andExpect(jsonPath("$.data.seats[0].status").value(1));
    }

    @Test
    void seed中已售的座位不能下单() throws Exception {
        // 活动 6 第 80 个座位（id=270）在 seed 里 status=1，下单阶段就该挡住，
        // 不能等到支付时才发现 DB 的 status=0 条件更新失败
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":6,\"seatId\":270}"))
                .andExpect(jsonPath("$.code").value("C409"));
    }
}
