package com.cdwater.ticket.auth.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String accessToken;

    private String refreshToken;

    private UserInfoVO userInfo;
}
