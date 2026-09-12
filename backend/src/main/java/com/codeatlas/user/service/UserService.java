package com.codeatlas.user.service;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.user.dto.UserVO;
import com.codeatlas.user.entity.User;
import com.codeatlas.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户查询服务。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 按用户名查询，用于「获取当前用户」。 */
    @Transactional(readOnly = true)
    public UserVO getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserVO.from(user);
    }

    @Transactional(readOnly = true)
    public UserVO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserVO.from(user);
    }
}
