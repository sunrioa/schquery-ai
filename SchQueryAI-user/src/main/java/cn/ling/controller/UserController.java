package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.pojo.User;
import cn.ling.domain.pojo.ImageStore;
import cn.ling.domain.dto.UserDTO;
import cn.ling.domain.vo.LoginResponse;
import cn.ling.domain.vo.LoginHistoryVO;
import cn.ling.domain.vo.UserManagementVO;
import cn.ling.domain.vo.PageResult;
import cn.ling.domain.vo.DashboardStatsVO;
import cn.ling.service.UserService;
import cn.ling.service.ImageStoreService;
import cn.ling.service.LoginHistoryService;
import cn.ling.service.OperationLogService;
import cn.ling.utils.Base64Utils;
import cn.ling.utils.ContextUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private ImageStoreService imageStoreService;

    @Resource
    private LoginHistoryService loginHistoryService;

    @Resource
    private OperationLogService operationLogService;

    /**
     * 记录操作日志的辅助方法
     */
    private void recordOperationLog(HttpServletRequest request, String action, String detail, boolean success) {
        try {
            // 从 ContextUtils 获取当前特散的用户名，如果没有则默认为admin
            String username = "admin";
            try {
                String contextUsername = ContextUtils.getUsername();
                if (contextUsername != null && !contextUsername.isEmpty()) {
                    username = contextUsername;
                }
            } catch (Exception e) {
                // 日志记录：从 Context 获取用户名失败，使用默认值
                log.debug("从 ContextUtils 获取用户名失败，使用默认值: {}", e.getMessage());
            }
            operationLogService.recordLog(username, action, detail, success ? 1 : 0, request);
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

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
    public Result<LoginResponse> login(@RequestBody UserDTO userDTO, HttpServletRequest request){
        return userService.login(userDTO, request);
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

    // 根据avatarId获取头像
    @GetMapping("/getAvatarById")
    public Result<String> getAvatarById(@RequestParam Long avatarId) {
        return userService.getAvatarById(avatarId);
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

    // 获取登录历史
    @GetMapping("/loginHistory")
    public Result<List<LoginHistoryVO>> getLoginHistory(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return loginHistoryService.getMyLoginHistory(pageNum, pageSize);
    }

    // 管理员功能：获取所有用户列表
    @GetMapping("/admin/allUsers")
    public Result<PageResult<UserManagementVO>> getAllUsers(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return userService.getAllUsers(pageNum, pageSize);
    }

    // 仪表板统计信息
    @GetMapping("/admin/dashboardStats")
    public Result<DashboardStatsVO> getDashboardStats() {
        return userService.getDashboardStats();
    }

    // 管理员功能：获取指定用户的登录历史
    @GetMapping("/admin/userLoginHistory")
    public Result<List<LoginHistoryVO>> getUserLoginHistory(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return loginHistoryService.getUserLoginHistory(userId, pageNum, pageSize);
    }

    // 管理员功能：拉黑用户
    @PostMapping("/admin/blacklistUser")
    public Result<String> blacklistUser(@RequestParam Long userId, HttpServletRequest request) {
        try {
            // 获取用户信息
            User user = userService.getById(userId);
            String userName = user != null ? user.getUserName() : "未知用户";
            
            Result<String> result = userService.blacklistUser(userId);
            if (result.getCode() == 200) {
                recordOperationLog(request, "拉黑用户", "拉黑用户: " + userName + " (ID: " + userId + ")", true);
            } else {
                recordOperationLog(request, "拉黑用户", "拉黑用户: " + userName + " (ID: " + userId + ")", false);
            }
            return result;
        } catch (Exception e) {
            recordOperationLog(request, "拉黑用户", "拉黑用户失败，ID: " + userId + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

    // 管理员功能：解除拉黑用户
    @PostMapping("/admin/unblacklistUser")
    public Result<String> unblacklistUser(@RequestParam Long userId, HttpServletRequest request) {
        try {
            // 获取用户信息
            User user = userService.getById(userId);
            String userName = user != null ? user.getUserName() : "未知用户";
            
            Result<String> result = userService.unblacklistUser(userId);
            if (result.getCode() == 200) {
                recordOperationLog(request, "解除拉黑", "解除拉黑用户: " + userName + " (ID: " + userId + ")", true);
            } else {
                recordOperationLog(request, "解除拉黑", "解除拉黑用户: " + userName + " (ID: " + userId + ")", false);
            }
            return result;
        } catch (Exception e) {
            recordOperationLog(request, "解除拉黑", "解除拉黑用户失败，ID: " + userId + "，原因: " + e.getMessage(), false);
            throw e;
        }
    }

}
