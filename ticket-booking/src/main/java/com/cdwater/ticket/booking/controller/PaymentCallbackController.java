package com.cdwater.ticket.booking.controller;

import com.cdwater.ticket.booking.dto.PayCallbackRequest;
import com.cdwater.ticket.booking.service.PaymentService;
import com.cdwater.ticket.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模拟微信异步回调，无用户凭据；真实接入时此处应校验微信签名 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentService paymentService;

    @PostMapping("/callback")
    public Result<Void> callback(@RequestBody @Valid PayCallbackRequest request) {
        paymentService.handleCallback(request.getOutTradeNo(), request.getTransactionId(),
                Boolean.TRUE.equals(request.getSuccess()));
        return Result.success();
    }
}
