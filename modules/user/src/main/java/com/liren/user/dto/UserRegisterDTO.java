package com.liren.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户注册请求参数")
public class UserRegisterDTO implements Serializable {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, message = "账号长度不能少于4位")
    @Schema(description = "用户账号")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于8位")
    @Schema(description = "用户密码")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, message = "确认密码长度不能少于8位")
    @Schema(description = "确认密码")
    private String checkPassword;
}