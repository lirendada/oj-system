package com.liren.system.controller;

import com.liren.common.core.result.Result;
import com.liren.system.dto.LoginRequestDTO;
import com.liren.system.service.ISystemUserService;
import com.liren.system.vo.LoginResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                        schema = @Schema(implementation = LoginRequestDTO.class)
            )
    ))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "6003", description = "用户不存在"),
            @ApiResponse(responseCode = "6005", description = "用户名或密码错误"),
    })
    public Result<LoginResponseVO> login(@Valid @RequestBody LoginRequestDTO loginDTO) {
        return Result.success(systemUserService.login(loginDTO));
    }


}
