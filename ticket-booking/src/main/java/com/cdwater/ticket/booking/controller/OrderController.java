package com.cdwater.ticket.booking.controller;

import com.cdwater.ticket.auth.security.LoginUser;
import com.cdwater.ticket.booking.dto.CancelRequest;
import com.cdwater.ticket.booking.dto.CreateOrderRequest;
import com.cdwater.ticket.booking.dto.PayRequest;
import com.cdwater.ticket.booking.service.OrderService;
import com.cdwater.ticket.booking.service.PaymentService;
import com.cdwater.ticket.booking.vo.CreateOrderVO;
import com.cdwater.ticket.booking.vo.OrderVO;
import com.cdwater.ticket.common.result.PageResult;
import com.cdwater.ticket.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private final PaymentService paymentService;

    @PostMapping("/orders")
    public Result<CreateOrderVO> create(@AuthenticationPrincipal LoginUser user,
                                       @RequestBody @Valid CreateOrderRequest request) {
        return Result.success(orderService.create(user.getUserId(), request));
    }

    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> list(@AuthenticationPrincipal LoginUser user,
                                           @RequestParam(required = false) String orderNo,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.list(user.getUserId(), orderNo, status, page, size));
    }

    @PostMapping("/orders/cancel")
    public Result<Void> cancel(@AuthenticationPrincipal LoginUser user,
                               @RequestBody @Valid CancelRequest request) {
        orderService.cancel(user.getUserId(), request.getOrderNo());
        return Result.success();
    }

    @PostMapping("/pay")
    public Result<Void> pay(@AuthenticationPrincipal LoginUser user,
                            @RequestBody @Valid PayRequest request) {
        paymentService.pay(user.getUserId(), request.getOrderNo());
        return Result.success();
    }
}
