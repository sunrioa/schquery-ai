package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.pojo.User;
import cn.ling.domain.pojo.ImageStore;
import cn.ling.domain.dto.UserDTO;
import cn.ling.service.UserService;
import cn.ling.service.ImageStoreService;
import cn.ling.utils.Base64Utils;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private ImageStoreService imageStoreService;

    //注册
    @PostMapping("/register")
    public Result<User> register(@RequestBody UserDTO userDTO){
        return userService.register(userDTO);
    }

    //发送注册验证码
    @PostMapping("/sendRegisterCode")
    public Result<String> sendRegisterCode(@RequestBody UserDTO userDTO){
        return userService.sendRegisterCode(userDTO);
    }

    //登录
    @PostMapping("/login")
    public Result<String> login(@RequestBody UserDTO userDTO){
        return userService.login(userDTO);
    }


    //修改密码
    @PostMapping("/updatePassword")
    public Result<String> updatePassword(@RequestBody UserDTO userDTO){
        return userService.updatePassword(userDTO);
    }

    //找回密码
    @PostMapping("/findPassword")
    public Result<String> findPassword(@RequestBody UserDTO userDTO){
        return userService.findPassword(userDTO);
    }

    //发送找回密码验证码
    @PostMapping("/sendFindPasswordCode")
    public Result<String> sendFindPasswordCode(@RequestBody UserDTO userDTO){
        return userService.sendFindPasswordCode(userDTO);
    }

    @PostMapping("/uploadAudioFile")
    public Result<String> uploadAudioFile(@RequestBody MultipartFile radioFile){
        return userService.uploadAudioFile(radioFile);
    }

    // 流式语音识别 - 开始会话
    @PostMapping("/streaming/start")
    public Result<String> startStreamingRecognition(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        return userService.startStreamingRecognition(sessionId);
    }

    // 流式语音识别 - 发送音频
    @PostMapping("/streaming/audio")
    public Result<String> sendStreamingAudio(@RequestParam("sessionToken") String sessionToken,
                                           @RequestParam("audioFile") MultipartFile audioFile) {
        return userService.sendStreamingAudio(sessionToken, audioFile);
    }

    // 流式语音识别 - 停止会话
    @PostMapping("/streaming/stop")
    public Result<String> stopStreamingRecognition(@RequestBody Map<String, String> request) {
        String sessionToken = request.get("sessionToken");
        return userService.stopStreamingRecognition(sessionToken);
    }

    // 流式语音识别 - 强制停止
    @PostMapping("/streaming/forceStop")
    public Result<String> forceStopStreamingRecognition(@RequestBody Map<String, String> request) {
        String sessionToken = request.get("sessionToken");
        return userService.forceStopStreamingRecognition(sessionToken);
    }

    // 上传头像
    @PostMapping("/uploadAvatar")
    public Result<Long> uploadAvatar(@RequestParam("avatarFile") MultipartFile avatarFile) {
        return userService.uploadAvatar(avatarFile);
    }

    // 获取用户头像
    @GetMapping("/getAvatar")
    public Result<String> getAvatar() {
        return userService.getUserAvatar();
    }

    // 更新用户头像
    @PostMapping("/updateAvatar")
    public Result<Long> updateAvatar(@RequestParam("avatarFile") MultipartFile avatarFile) {
        return userService.updateAvatar(avatarFile);
    }

    // 获取用户信息
    @GetMapping("/getUserInfo")
    public Result<User> getUserInfo() {
        return userService.getUserInfo();
    }

    // 删除用户头像
    @PostMapping("/deleteAvatar")
    public Result<String> deleteAvatar() {
        return userService.deleteAvatar();
    }

}
