package com.cdwater.ticket.booking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayRequest {

    /** 收字符串而非 long：与响应保持同一形态，前端拿到即可原样回传 */
    @NotBlank
    private String orderNo;
}
