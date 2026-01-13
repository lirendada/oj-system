package com.liren.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户更新个人信息请求")
public class UserUpdateMyDTO implements Serializable {

    @Schema(description = "用户昵称")
    private String nickName;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "学校")
    private String school;
}