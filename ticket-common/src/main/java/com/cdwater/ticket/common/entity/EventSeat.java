package com.cdwater.ticket.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_event_seat")
public class EventSeat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Integer rowNo;

    private Integer colNo;

    private Integer status;
}
