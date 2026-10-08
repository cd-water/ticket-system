package com.cdwater.ticket.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cdwater.ticket.auth.mapper.UserMapper;
import com.cdwater.ticket.common.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 测试夹具：造用户、清用户。生产代码不引用。 */
@Component
@RequiredArgsConstructor
public class TestSupport {

    private final UserMapper userMapper;

    /** 造一批手机号唯一的用户；「一人一单」并发测试要求每个线程是不同用户 */
    public List<Long> createUsers(int count, String phonePrefix) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setPhone(phonePrefix + "-" + i + "-" + System.nanoTime());
            userMapper.insert(user);
            ids.add(user.getId());
        }
        return ids;
    }

    /** 按前缀清理历史测试用户，避免多次运行后表膨胀 */
    public void deleteUsersByPrefix(String phonePrefix) {
        userMapper.delete(Wrappers.<User>lambdaQuery().likeRight(User::getPhone, phonePrefix));
    }
}
