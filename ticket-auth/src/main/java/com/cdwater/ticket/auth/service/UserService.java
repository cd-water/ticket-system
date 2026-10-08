package com.cdwater.ticket.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.auth.mapper.UserMapper;
import com.cdwater.ticket.common.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public User findById(long id) {
        return userMapper.selectById(id);
    }

    public User findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
    }

    public User register(String phone) {
        User user = new User();
        user.setPhone(phone);
        userMapper.insert(user);
        return user;
    }

    public void updatePassword(long userId, String encoded) {
        User user = new User();
        user.setId(userId);
        user.setPassword(encoded);
        userMapper.updateById(user);
    }
}
