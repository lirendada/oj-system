package com.liren.user.controller;

import com.liren.common.core.context.UserContext;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.user.dto.*;
import com.liren.user.exception.UserException;
import com.liren.user.service.IUserService;
import com.liren.user.vo.UserLoginVO;
import com.liren.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "C端用户API")
public class UserController {
    @Autowired
    private IUserService userService;

    @PostMapping("/login")
    @Operation(
            summary = "用户登录",
            description = "返回 Token 及用户基本信息",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "登录信息",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserLoginDTO.class)
                    )
    ))
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        // 这里直接返回 Service 的结果即可
        return Result.success(userService.login(loginDTO));
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前登录用户信息")
    public Result<UserVO> getInfo() {
        // 1. 从 ThreadLocal/Token 中获取当前用户ID
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
        }

        // 2. 查询用户信息
        UserVO userVO = userService.getUserInfo(userId);
        return Result.success(userVO);
    }

    @PostMapping("/register")
    @Operation(
        summary = "用户注册",
        description = "用户注册接口，返回 Token 及用户基本信息",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                description = "登录信息",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = UserRegisterDTO.class)
                )
        )
    )
    public Result<Long> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        if (userRegisterDTO == null) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求参数不能为空");
        }
        Long userId = userService.register(userRegisterDTO);
        return Result.success(userId);
    }

    @PostMapping("/forget/send-code")
    @Operation(summary = "发送忘记密码验证码")
    public Result<Void> sendForgetCode(@Valid @RequestBody UserSendCodeDTO req) {
        userService.sendForgetPasswordCode(req.getEmail());
        return Result.success();
    }

    @PostMapping("/forget/reset")
    @Operation(summary = "重置密码")
    public Result<Void> resetPassword(@Valid @RequestBody UserResetPassDTO req) {
        userService.resetPassword(req);
        return Result.success();
    }

    @PostMapping("/update/my")
    @Operation(summary = "更新个人信息")
    public Result<Boolean> updateMyInfo(@RequestBody UserUpdateMyDTO userUpdateMyDTO) {
        if (userUpdateMyDTO == null) {
            throw new UserException(ResultCode.PARAM_ERROR);
        }
        boolean result = userService.updateMyInfo(userUpdateMyDTO);
        return Result.success(result);
    }
}