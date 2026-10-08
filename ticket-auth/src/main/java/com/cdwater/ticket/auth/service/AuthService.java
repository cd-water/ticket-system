package com.cdwater.ticket.auth.service;

import com.cdwater.ticket.auth.security.JwtProperties;
import com.cdwater.ticket.auth.security.JwtService;
import com.cdwater.ticket.auth.vo.LoginVO;
import com.cdwater.ticket.auth.vo.UserInfoVO;
import com.cdwater.ticket.common.constant.RedisKey;
import com.cdwater.ticket.common.entity.User;
import com.cdwater.ticket.common.enums.ResultCode;
import com.cdwater.ticket.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final SmsCodeService smsCodeService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;

    public String sendCode(String phone) {
        return smsCodeService.send(phone);
    }

    public boolean isEchoCodeEnabled() {
        return smsCodeService.isEchoEnabled();
    }

    public LoginVO smsLogin(String phone, String code) {
        if (!smsCodeService.verify(phone, code)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "验证码错误或已过期");
        }
        User user = userService.findByPhone(phone);
        return issue(user == null ? userService.register(phone) : user);
    }

    public LoginVO pwdLogin(String phone, String password) {
        User user = userService.findByPhone(phone);
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "手机号或密码错误");
        }
        return issue(user);
    }

    public LoginVO refresh(String refreshToken) {
        String key = RedisKey.refreshToken(refreshToken);
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录已失效，请重新登录");
        }
        // 轮换：旧令牌立即失效，堵住重放
        redis.delete(key);
        User user = userService.findById(Long.parseLong(userId));
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "登录已失效，请重新登录");
        }
        return issue(user);
    }

    public void logout(String refreshToken) {
        String key = RedisKey.refreshToken(refreshToken);
        String userId = redis.opsForValue().get(key);
        redis.delete(key);
        if (userId != null) {
            redis.opsForSet().remove(RedisKey.userTokens(Long.parseLong(userId)), refreshToken);
        }
    }

    public void changePassword(long userId, String newPassword) {
        userService.updatePassword(userId, passwordEncoder.encode(newPassword));
        // 改密后该用户全部会话失效
        String index = RedisKey.userTokens(userId);
        Set<String> tokens = redis.opsForSet().members(index);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(RedisKey::refreshToken).toList());
        }
        redis.delete(index);
    }

    private LoginVO issue(User user) {
        String accessToken = jwtService.createAccessToken(user.getId());
        String refreshToken = UUID.randomUUID().toString();

        redis.opsForValue().set(RedisKey.refreshToken(refreshToken),
                String.valueOf(user.getId()), jwtProperties.getRefreshTtl());
        String index = RedisKey.userTokens(user.getId());
        redis.opsForSet().add(index, refreshToken);
        redis.expire(index, jwtProperties.getRefreshTtl());

        UserInfoVO info = new UserInfoVO();
        info.setId(user.getId());
        info.setPhone(user.getPhone());
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setUserInfo(info);
        return vo;
    }
}
