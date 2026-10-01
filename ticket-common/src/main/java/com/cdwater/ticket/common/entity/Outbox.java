package com.cdwater.ticket.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_outbox")
public class Outbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String topic;

    private String messageKey;

    private String payload;

    private LocalDateTime deliverTime;

    private Integer status;
}
