package com.cdwater.ticket.booking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotNull
    private Long eventId;

    /** 选座模式必填，抢票模式忽略 */
    private Long seatId;
}
