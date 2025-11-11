package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.pojo.User;
import cn.ling.domain.dto.UserDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

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

    // 流式语音识别接口
    Result<String> startStreamingRecognition(String sessionId);

    Result<String> sendStreamingAudio(String sessionToken, MultipartFile audioFile);

    Result<String> stopStreamingRecognition(String sessionToken);

    Result<String> forceStopStreamingRecognition(String sessionToken);

    // 头像相关接口
    Result<Long> uploadAvatar(MultipartFile avatarFile);

    Result<String> getUserAvatar();

    Result<Long> updateAvatar(MultipartFile avatarFile);

    Result<User> getUserInfo();

    Result<String> deleteAvatar();
}
