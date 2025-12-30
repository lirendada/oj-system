package com.liren.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户登录请求参数")
public class UserLoginDTO implements Serializable {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "用户账号")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "用户密码")
    private String password;
}