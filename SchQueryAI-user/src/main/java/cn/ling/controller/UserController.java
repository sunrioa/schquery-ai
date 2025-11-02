package cn.ling.controller;

import cn.ling.domain.Result;
import cn.ling.domain.User;
import cn.ling.domain.dto.UserDTO;
import cn.ling.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    @Resource
    private UserService userService;

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

}
