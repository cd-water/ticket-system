package com.cdwater.ticket.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long outTradeNo;

    private Long orderId;

    private BigDecimal amount;

    private Integer status;

    private String codeUrl;

    private String transactionId;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;
}
