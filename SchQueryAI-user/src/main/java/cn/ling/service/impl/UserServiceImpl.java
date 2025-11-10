package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.dto.UserDTO;
import cn.ling.domain.pojo.ImageStore;
import cn.ling.service.AIService;
import cn.ling.service.ImageStoreService;
import cn.ling.utils.*;
import cn.ling.exception.CustomException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.User;
import cn.ling.service.UserService;
import cn.ling.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Resource
    private EmailUtils emailUtils;

    @Resource
    private NumberUtils numberUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ImageStoreService imageStoreService;

    private final static String REGISTER_CODE_KEY = "register_code:";
    private final static String FIND_PASSWORD_CODE_KEY = "find_password_code:";
    private final static String TOKEN_BLACKLIST_KEY = "token_blacklist:";

    @Override
    public Result<String> sendRegisterCode(UserDTO userDTO) {
        // 参数校验
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            throw CustomException.error("邮箱不能为空");
        }

        // 验证邮箱格式
        if (!userDTO.getEmail().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw CustomException.error("邮箱格式不正确");
        }

        // 检查邮箱是否已被注册
        User existUser = lambdaQuery().eq(User::getEmail, userDTO.getEmail()).one();

        if (existUser != null) {
            throw CustomException.error("该邮箱已被注册");
        }

        try {
            // 生成6位数字验证码
            String code = String.valueOf(numberUtils.generateDigitCode(6));

            // 存储验证码到Redis，设置5分钟过期时间
            String redisKey = REGISTER_CODE_KEY + userDTO.getEmail();
            stringRedisTemplate.opsForValue().set(redisKey, code, 300, TimeUnit.SECONDS);

            // 发送邮件
            String subject = "SchQueryAI - 注册验证码";
            String content = "您好，\n\n您正在注册SchQueryAI账号，验证码为：" + code +
                           "\n\n验证码有效期为5分钟，请及时使用。" +
                           "\n\n如果这不是您本人操作，请忽略此邮件。";

            Boolean sendResult = emailUtils.sendEmail(userDTO.getEmail(), subject, content);

            if (sendResult) {
                return Result.success("验证码发送成功，请查收邮件");
            } else {
                throw CustomException.error("验证码发送失败，请稍后重试");
            }
        } catch (Exception e) {
            throw CustomException.error("验证码发送失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<User> register(UserDTO userDTO) {
        try {
            // 参数校验
            if (!checkInfo(userDTO)) {
                throw CustomException.error("注册信息校验失败");
            }

            // 验证邮箱验证码
            String redisKey = REGISTER_CODE_KEY + userDTO.getEmail();
            String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
            if (storedCode == null) {
                throw CustomException.error("验证码已过期，请重新获取");
            }
            if (!storedCode.equals(userDTO.getCode())) {
                throw CustomException.error("验证码错误");
            }

            // 创建新用户
            User newUser = new User();
            newUser.setUserName(userDTO.getUserName());
            newUser.setPassWord(BCryptUtils.encode(userDTO.getPassword()));
            newUser.setEmail(userDTO.getEmail());
            newUser.setCreateTime(new Date());
            newUser.setUpdateTime(new Date());
            newUser.setRole("user");

            boolean save = save(newUser);

            if (save) {
                // 删除已使用的验证码
                stringRedisTemplate.delete(redisKey);
                // 清空密码信息后返回
                newUser.setPassWord(null);
                return Result.success(newUser, "注册成功");
            } else {
                throw CustomException.error("注册失败");
            }
        } catch (Exception e) {
            throw CustomException.error("注册失败：" + e.getMessage());
        }
    }

    private boolean checkInfo(UserDTO userDTO) {
        if (userDTO.getUserName() == null || userDTO.getUserName().trim().isEmpty()) {
            throw CustomException.error("请输入账号");
        }

        if (userDTO.getUserName().length() <= 5) {
            throw CustomException.error("账号最小为6位");
        }

        // 检查用户名是否已存在
        User existUserByUserName = lambdaQuery().eq(User::getUserName, userDTO.getUserName()).one();

        if (existUserByUserName != null) {
            throw CustomException.error("账号已存在");
        }

        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            throw CustomException.error("请输入密码");
        }
        if (userDTO.getPassword().length() <= 5) {
            throw CustomException.error("密码最小为6位");
        }
        if (userDTO.getRePassword() == null || userDTO.getRePassword().trim().isEmpty()) {
            throw CustomException.error("请输入确认密码");
        }
        if (!userDTO.getPassword().equals(userDTO.getRePassword())) {
            throw CustomException.error("两次密码不一致");
        }
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            throw CustomException.error("请输入邮箱");
        }
        if (!userDTO.getEmail().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw CustomException.error("邮箱格式错误");
        }

        return true;
    }

    @Override
    public Result<String> login(UserDTO userDTO) {
        // 参数校验
        if (userDTO.getUserName() == null || userDTO.getUserName().trim().isEmpty()) {
            throw CustomException.error("用户名不能为空");
        }
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            throw CustomException.error("密码不能为空");
        }

        // 查询用户
        User user = lambdaQuery().eq(User::getUserName, userDTO.getUserName()).one();

        if (user == null) {
            throw CustomException.error("用户不存在");
        }

        // 验证密码
        if (!BCryptUtils.judge(userDTO.getPassword(), user.getPassWord())) {
            throw CustomException.error("密码错误");
        }

        String token = JwtUtils.generateToken("用户信息", new HashMap<>(){
            {
                put("userId", user.getId());
                put("userName", user.getUserName());
                put("role", user.getRole());
            }
        });

        return Result.success(token);
    }

    //修改密码
    @Override
    public Result<String> updatePassword(UserDTO userDTO) {
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            throw CustomException.error("原密码不能为空");
        }
        if (userDTO.getRePassword() == null || userDTO.getRePassword().trim().isEmpty()) {
            throw CustomException.error("新密码不能为空");
        }

        Long userId = ContextUtils.getUserId();

        // 查询用户
        User user = lambdaQuery().eq(User::getId, userId).one();
        if (user == null) {
            throw CustomException.error("用户不存在");
        }

        // 验证原密码
        if (!BCryptUtils.judge(userDTO.getPassword(), user.getPassWord())) {
            throw CustomException.error("原密码错误");
        }

        // 更新密码
        user.setPassWord(BCryptUtils.encode(userDTO.getRePassword()));
        user.setUpdateTime(new Date());
        boolean update = updateById(user);

        if (update) {
            return Result.success("密码修改成功");
        } else {
            throw CustomException.error("密码修改失败");
        }
    }

    //找回密码
    @Override
    public Result<String> findPassword(UserDTO userDTO) {
        // 参数校验
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            throw CustomException.error("邮箱不能为空");
        }
        if (userDTO.getCode() == null || userDTO.getCode().trim().isEmpty()) {
            throw CustomException.error("验证码不能为空");
        }
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            throw CustomException.error("新密码不能为空");
        }

        try {
            // 验证邮箱验证码
            String redisKey = FIND_PASSWORD_CODE_KEY + userDTO.getEmail();
            String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
            if (storedCode == null) {
                throw CustomException.error("验证码已过期，请重新获取");
            }
            if (!storedCode.equals(userDTO.getCode())) {
                throw CustomException.error("验证码错误");
            }

            // 查询用户
            User user = lambdaQuery().eq(User::getEmail, userDTO.getEmail()).one();
            if (user == null) {
                throw CustomException.error("该邮箱未注册");
            }

            // 更新密码
            user.setPassWord(BCryptUtils.encode(userDTO.getPassword()));
            user.setUpdateTime(new Date());
            boolean update = updateById(user);

            if (update) {
                // 删除已使用的验证码
                stringRedisTemplate.delete(redisKey);

                // 将该用户的所有现有令牌加入黑名单（强制所有设备重新登录）
                String userBlacklistKey = TOKEN_BLACKLIST_KEY + user.getId();
                long currentTime = System.currentTimeMillis() / 1000; // Unix时间戳（秒）

                // 设置黑名单记录，TTL为2小时（与JWT过期时间一致）
                stringRedisTemplate.opsForValue().set(userBlacklistKey, String.valueOf(currentTime), 7200, TimeUnit.SECONDS);

                return Result.success("密码重置成功");
            } else {
                throw CustomException.error("密码重置失败");
            }
        } catch (Exception e) {
            throw CustomException.error("密码重置失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> sendFindPasswordCode(UserDTO userDTO) {
        // 参数校验
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            throw CustomException.error("邮箱不能为空");
        }

        // 验证邮箱格式
        if (!userDTO.getEmail().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            throw CustomException.error("邮箱格式不正确");
        }


        // 检查邮箱是否存在
        User user = lambdaQuery().eq(User::getEmail, userDTO.getEmail()).one();
        if (user == null) {
            throw CustomException.error("该邮箱未注册");
        }

        // 忘记密码功能不需要验证用户名，用户通常只记得邮箱


        try {
            // 生成6位数字验证码
            String code = String.valueOf(numberUtils.generateDigitCode(6));

            // 存储验证码到Redis，设置5分钟过期时间
            String redisKey = FIND_PASSWORD_CODE_KEY + userDTO.getEmail();
            stringRedisTemplate.opsForValue().set(redisKey, code, 300, TimeUnit.SECONDS);

            // 发送邮件
            String subject = "SchQueryAI - 找回密码验证码";
            String content = "您好，\n\n您正在重置SchQueryAI账号密码，验证码为：" + code +
                           "\n\n验证码有效期为5分钟，请及时使用。" +
                           "\n\n如果这不是您本人操作，请忽略此邮件或联系客服。";

            Boolean sendResult = emailUtils.sendEmail(userDTO.getEmail(), subject, content);

            if (sendResult) {
                return Result.success("验证码发送成功，请查收邮件");
            } else {
                throw CustomException.error("验证码发送失败，请稍后重试");
            }
        } catch (Exception e) {
            throw CustomException.error("验证码发送失败：" + e.getMessage());
        }
    }

    @Autowired
    private AIService aiService;

    @Override
    public Result<String> uploadAudioFile(MultipartFile radioFile) {
        return aiService.audioToText(radioFile);
    }

    @Override
    public Result<String> startStreamingRecognition(String sessionId) {
        return aiService.startStreamingRecognition(sessionId);
    }

    @Override
    public Result<String> sendStreamingAudio(String sessionToken, MultipartFile audioFile) {
        return aiService.sendStreamingAudio(sessionToken, audioFile);
    }

    @Override
    public Result<String> stopStreamingRecognition(String sessionToken) {
        return aiService.stopStreamingRecognition(sessionToken);
    }

    @Override
    public Result<String> forceStopStreamingRecognition(String sessionToken) {
        return aiService.forceStopStreamingRecognition(sessionToken);
    }

    @Override
    @Transactional
    public Result<Long> uploadAvatar(MultipartFile avatarFile) {
        try {
            // 参数校验
            if (avatarFile == null || avatarFile.isEmpty()) {
                throw CustomException.error("请选择要上传的头像文件");
            }

            // 检查文件大小（限制为5MB）
            if (avatarFile.getSize() > 5 * 1024 * 1024) {
                throw CustomException.error("头像文件大小不能超过5MB");
            }

            // 检查文件类型
            String contentType = avatarFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw CustomException.error("请上传图片格式的文件");
            }

            // 转换为base64
            String base64Image = Base64Utils.imageToBase64(avatarFile);
            if (base64Image == null) {
                throw CustomException.error("头像文件转换失败");
            }

            // 创建图片存储记录
            ImageStore imageStore = new ImageStore();
            imageStore.setImageName(avatarFile.getOriginalFilename());
            imageStore.setImageBase64(base64Image);
            imageStore.setCreatedAt(new Date());

            // 保存到数据库
            boolean saved = imageStoreService.save(imageStore);
            if (!saved) {
                throw CustomException.error("头像保存失败");
            }

            return Result.success(imageStore.getId(), "头像上传成功");

        } catch (Exception e) {
            if (e instanceof CustomException) {
                throw e;
            }
            throw CustomException.error("头像上传失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> getUserAvatar() {
         Long userId = ContextUtils.getUserId();
         User currentUser = getById(userId);

         // 如果用户没有头像，返回空字符串
         if (currentUser.getAvatar() == null) {
             return Result.success("");
         }

         // 获取头像信息
         ImageStore imageStore = imageStoreService.getById(currentUser.getAvatar());
         if (imageStore == null) {
             return Result.success("");
         }

         return Result.success(imageStore.getImageBase64());
    }

    @Override
    @Transactional
    public Result<Long> updateAvatar(MultipartFile avatarFile) {
        try {
            // 参数校验
            if (avatarFile == null || avatarFile.isEmpty()) {
                throw CustomException.error("请选择要上传的头像文件");
            }

            // 检查文件大小（限制为5MB）
            if (avatarFile.getSize() > 5 * 1024 * 1024) {
                throw CustomException.error("头像文件大小不能超过5MB");
            }

            // 检查文件类型
            String contentType = avatarFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw CustomException.error("请上传图片格式的文件");
            }

            // 从ThreadLocal获取当前用户信息
            Long userId = ContextUtils.getUserId();

            User currentUser = getById(userId);
            if (currentUser == null) {
                throw CustomException.error("用户未登录");
            }

            // 转换为base64
            String base64Image = Base64Utils.imageToBase64(avatarFile);
            if (base64Image == null) {
                throw CustomException.error("头像文件转换失败");
            }

            // 创建新的图片存储记录
            ImageStore imageStore = new ImageStore();
            imageStore.setImageName(avatarFile.getOriginalFilename());
            imageStore.setImageBase64(base64Image);
            imageStore.setCreatedAt(new Date());

            // 保存新头像到数据库
            boolean saved = imageStoreService.save(imageStore);
            if (!saved) {
                throw CustomException.error("头像保存失败");
            }

            // 删除旧头像（如果存在）
            if (currentUser.getAvatar() != null) {
                imageStoreService.removeById(currentUser.getAvatar());
            }

            // 更新用户头像信息
            currentUser.setAvatar(imageStore.getId());
            boolean updated = this.updateById(currentUser);
            if (!updated) {
                throw CustomException.error("用户头像更新失败");
            }

            return Result.success(imageStore.getId(), "头像更新成功");

        } catch (Exception e) {
            if (e instanceof CustomException) {
                throw e;
            }
            throw CustomException.error("头像更新失败：" + e.getMessage());
        }
    }

    @Override
    public Result<User> getUserInfo() {
        try {
            Long userId = ContextUtils.getUserId();
            User currentUser = getById(userId);

            if (currentUser == null) {
                throw CustomException.error("用户信息不存在");
            }

            // 清除密码等敏感信息
            currentUser.setPassWord(null);

            return Result.success(currentUser, "获取用户信息成功");

        } catch (Exception e) {
            if (e instanceof CustomException) {
                throw e;
            }
            throw CustomException.error("获取用户信息失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<String> deleteAvatar() {
        try {
            Long userId = ContextUtils.getUserId();
            User currentUser = getById(userId);

            if (currentUser == null) {
                throw CustomException.error("用户信息不存在");
            }

            // 如果用户没有头像，直接返回成功
            if (currentUser.getAvatar() == null) {
                return Result.success("用户暂未设置头像");
            }

            // 删除图片存储记录
            boolean deleted = imageStoreService.removeById(currentUser.getAvatar());
            if (!deleted) {
                throw CustomException.error("头像删除失败");
            }

            // 更新用户头像信息为null
            currentUser.setAvatar(null);
            boolean updated = this.updateById(currentUser);
            if (!updated) {
                throw CustomException.error("用户信息更新失败");
            }

            return Result.success("头像删除成功");

        } catch (Exception e) {
            if (e instanceof CustomException) {
                throw e;
            }
            throw CustomException.error("删除头像失败：" + e.getMessage());
        }
    }
}