package com.cdwater.ticket.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderNo;

    private Long userId;

    private Long eventId;

    private Long seatId;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;
}
