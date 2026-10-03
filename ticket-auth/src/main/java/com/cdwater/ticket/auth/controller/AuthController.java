package com.cdwater.ticket.auth.controller;

import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 认证接口 Mock 实现：只回传造好的数据，不连数据库、不发短信、不签发真实 JWT。
 * 真实实现到位后整体替换为 service 层调用。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Mock 短信验证码：固定值，前端填 123456 即可登录 */
    private static final String MOCK_SMS_CODE = "123456";

    /** Mock 密码：与 seed.sql 中 13800000001 的 BCrypt 明文一致 */
    private static final String MOCK_PASSWORD = "Aa123456";

    private static final Long MOCK_USER_ID = 1L;
    private static final String MOCK_PHONE = "13800000001";

    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody @Valid SendCodeRequest request) {
        // Mock：不真的发短信，验证码恒为 MOCK_SMS_CODE
        return Result.success();
    }

    @PostMapping("/sms-login")
    public Result<LoginVO> smsLogin(@RequestBody @Valid SmsLoginRequest request) {
        if (!MOCK_SMS_CODE.equals(request.getCode())) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "验证码错误");
        }
        return Result.success(login());
    }

    @PostMapping("/pwd-login")
    public Result<LoginVO> pwdLogin(@RequestBody @Valid PwdLoginRequest request) {
        if (!MOCK_PHONE.equals(request.getPhone()) || !MOCK_PASSWORD.equals(request.getPassword())) {
            return Result.fail(ResultCode.BAD_REQUEST.getCode(), "手机号或密码错误");
        }
        return Result.success(login());
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody @Valid RefreshRequest request) {
        // Mock：不做真实轮换校验，签发一对新 Token 即可
        return Result.success(login());
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody @Valid RefreshRequest request) {
        return Result.success();
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        return Result.success();
    }

    /** 造一对 Token：access 伪装成 JWT 形态，refresh 用 UUID，与接口文档示例一致 */
    private static LoginVO login() {
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(MOCK_USER_ID);
        userInfo.setPhone(MOCK_PHONE);

        LoginVO vo = new LoginVO();
        vo.setAccessToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJzdWIiOiIxIiwiaWF0IjoxNzYwMDAwMDAwMH0"
                + ".mock-signature");
        vo.setRefreshToken(UUID.randomUUID().toString());
        vo.setUserInfo(userInfo);
        return vo;
    }

    @Data
    public static class SendCodeRequest {
        @NotBlank
        private String phone;
    }

    @Data
    public static class SmsLoginRequest {
        @NotBlank
        private String phone;
        @NotBlank
        private String code;
    }

    @Data
    public static class PwdLoginRequest {
        @NotBlank
        private String phone;
        @NotBlank
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        @Size(min = 8, max = 20)
        private String newPassword;
    }

    @Data
    public static class LoginVO {
        private String accessToken;
        private String refreshToken;
        private UserInfoVO userInfo;
    }

    @Data
    public static class UserInfoVO {
        private Long id;
        private String phone;
    }
}
