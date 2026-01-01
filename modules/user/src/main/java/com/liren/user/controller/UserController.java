package com.liren.user.controller;

import com.liren.common.core.result.Result;
import com.liren.user.dto.UserLoginDTO;
import com.liren.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Tag(name = "C端用户API")
public class UserController {
    @Autowired
    private IUserService userService;

    @PostMapping("/login")
    @Operation(
            summary = "用户登录",
            description = "返回 JWT Token",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "登录信息",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserLoginDTO.class)
                    )
    ))
    public Result<String> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        return Result.success(userService.login(loginDTO));
    }
}