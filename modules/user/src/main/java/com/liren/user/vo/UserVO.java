package com.liren.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户个人信息VO")
public class UserVO implements Serializable {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户账号")
    private String userAccount; // 对应表字段 user_account

    @Schema(description = "用户昵称")
    private String nickName; // 对应表字段 nick_name

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "学校")
    private String school;

    @Schema(description = "状态：0-禁用 1-正常")
    private Integer status;

    @Schema(description = "提交次数")
    private Integer submittedCount; // 对应表字段 submitted_count

    @Schema(description = "通过次数")
    private Integer acceptedCount; // 对应表字段 accepted_count

    @Schema(description = "Rating分数")
    private Integer rating;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}