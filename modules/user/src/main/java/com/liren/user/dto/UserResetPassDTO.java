package com.liren.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserResetPassDTO {
    @Schema(description = "邮箱地址")
    @NotBlank(message = "邮箱不能为空")
    private String email;

    @Schema(description = "验证码")
    @NotBlank(message = "验证码不能为空")
    private String code;

    @Schema(description = "新密码")
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}