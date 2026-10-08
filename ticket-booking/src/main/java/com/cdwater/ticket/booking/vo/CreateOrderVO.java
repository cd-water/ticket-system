package com.cdwater.ticket.booking.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateOrderVO {

    /** 雪花算法 ID 超出 JS 安全整数范围，以字符串返回避免前端丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private long orderNo;

    private BigDecimal amount;

    private LocalDateTime expireTime;

    private String eventName;

    private String eventAddress;

    /** 抢票模式为 null */
    private SeatPositionVO seat;
}
