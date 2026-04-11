package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;
import org.ymz.app.mapper.UserMapper;
import org.ymz.app.model.entity.User;
import org.ymz.app.service.UserService;

/**
 * 用户表 服务层实现。
 *
 * @author ymz
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
