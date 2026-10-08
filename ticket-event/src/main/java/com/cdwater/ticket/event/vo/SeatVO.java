package com.cdwater.ticket.event.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatVO {

    private int rowNo;

    private int colNo;

    private long seatId;

    /** 0-可选 1-不可选（已售与锁定中合并，前端不区分） */
    private int status;
}
