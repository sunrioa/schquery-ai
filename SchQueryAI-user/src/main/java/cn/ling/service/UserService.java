package cn.ling.service;

import cn.ling.domain.Result;
import cn.ling.domain.User;
import cn.ling.domain.dto.UserDTO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Administrator
* @description 针对表【user(系统用户表)】的数据库操作Service
* @createDate 2025-10-31 00:07:11
*/
public interface UserService extends IService<User> {

    Result<User> register(UserDTO userDTO);

    Result<String> login(UserDTO userDTO);

    Result<String> sendRegisterCode(UserDTO userDTO);

    Result<String> updatePassword(UserDTO userDTO);

    Result<String> findPassword(UserDTO userDTO);

    Result<String> sendFindPasswordCode(UserDTO userDTO);
}
