package com.cdwater.ticket.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayCallbackRequest {

    /** 商户订单号，对应 t_payment.out_trade_no */
    @NotBlank
    private String outTradeNo;

    @NotBlank
    private String transactionId;

    /** Mock 用：true=支付成功 false=支付失败 */
    @NotNull
    private Boolean success;
}
