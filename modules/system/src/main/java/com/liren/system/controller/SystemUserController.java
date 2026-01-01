package com.liren.system.controller;

import com.liren.common.core.context.UserContext;
import com.liren.common.core.result.Result;
import com.liren.common.core.utils.BCryptUtil;
import com.liren.system.dto.SystemUserLoginDTO;
import com.liren.system.service.ISystemUserService;
import com.liren.system.vo.SystemUserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/system/user")
@Tag(name = "管理员用户API")
public class SystemUserController {
    @Autowired
    private ISystemUserService systemUserService;

    @PostMapping("/login")
    @Operation(
            summary = "用户登录",
            description = "用户登录接口",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                description = "登录信息",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = SystemUserLoginDTO.class)
                )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "6003", description = "用户不存在"),
            @ApiResponse(responseCode = "6005", description = "用户名或密码错误"),
    })
    public Result<SystemUserLoginVO> login(@Valid @RequestBody SystemUserLoginDTO systemUserLoginDTO) {
        return Result.success(systemUserService.login(systemUserLoginDTO));
    }

//    @RequestMapping("/test")
//    public Result<String> test() {
//        String password = "123456";
//        String encoded = BCryptUtil.encode(password);
//        System.out.println("加密后的密码: " + encoded);
//
//        // 测试验证
//        boolean match = BCryptUtil.isMatch("123456", encoded);
//        System.out.println("验证结果: " + match);
//        return Result.success("test");
//    }
//
//    @GetMapping("/test/me")
//    public Result<Long> getMyId() {
//        // 直接从上下文获取 ID，不需要传参
//        Long userId = UserContext.getUserId();
//        System.out.println("当前登录用户ID: " + userId);
//        return Result.success(userId);
//    }
}
