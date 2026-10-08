package com.cdwater.ticket.auth.controller;

import com.cdwater.ticket.auth.dto.ChangePasswordRequest;
import com.cdwater.ticket.auth.dto.PwdLoginRequest;
import com.cdwater.ticket.auth.dto.RefreshRequest;
import com.cdwater.ticket.auth.dto.SendCodeRequest;
import com.cdwater.ticket.auth.dto.SmsLoginRequest;
import com.cdwater.ticket.auth.security.LoginUser;
import com.cdwater.ticket.auth.service.AuthService;
import com.cdwater.ticket.auth.vo.LoginVO;
import com.cdwater.ticket.common.result.Result;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-code")
    public Result<SendCodeVO> sendCode(@RequestBody @Valid SendCodeRequest request) {
        String code = authService.sendCode(request.getPhone());
        // 无短信服务商依赖：ticket.sms.echo-code 只控制「是否回传给前端」，
        // 生成与入库必须无条件执行，否则生产默认配置下短信登录永远无法成功
        SendCodeVO vo = new SendCodeVO();
        if (authService.isEchoCodeEnabled()) {
            vo.setCode(code);
        }
        return Result.success(vo);
    }

    @PostMapping("/sms-login")
    public Result<LoginVO> smsLogin(@RequestBody @Valid SmsLoginRequest request) {
        return Result.success(authService.smsLogin(request.getPhone(), request.getCode()));
    }

    @PostMapping("/pwd-login")
    public Result<LoginVO> pwdLogin(@RequestBody @Valid PwdLoginRequest request) {
        return Result.success(authService.pwdLogin(request.getPhone(), request.getPassword()));
    }

    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody @Valid RefreshRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody @Valid RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return Result.success();
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser user,
                                       @RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(user.getUserId(), request.getNewPassword());
        return Result.success();
    }

    @Data
    public static class SendCodeVO {

        /** 生产环境恒为 null */
        private String code;
    }
}
