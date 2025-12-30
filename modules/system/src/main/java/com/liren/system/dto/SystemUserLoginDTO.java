package com.liren.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录请求")
public class SystemUserLoginDTO {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;
}
