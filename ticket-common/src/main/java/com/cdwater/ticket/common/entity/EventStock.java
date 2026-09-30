package com.cdwater.ticket.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_event_stock")
public class EventStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Integer stock;
}
