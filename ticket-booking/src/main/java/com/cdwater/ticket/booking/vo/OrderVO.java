package com.cdwater.ticket.booking.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private long orderNo;

    private Long eventId;

    private String eventName;

    private String eventAddress;

    private BigDecimal eventPrice;

    private BigDecimal amount;

    private Integer status;

    private SeatPositionVO seat;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;
}
