package com.liren.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "系统用户登录VO")
public class SystemUserLoginVO {
    @Schema(description = "系统用户ID")
    private Long userId;

    @Schema(description = "系统用户名称")
    private String userAccount;

    @Schema(description = "系统用户昵称")
    private String nickName;

    // 【新增】返回 Token 给前端
    private String token;
}
