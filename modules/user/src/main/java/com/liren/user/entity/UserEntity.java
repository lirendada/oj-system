package com.liren.user.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.liren.common.core.base.BaseTimeEntity;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("tb_user")
public class UserEntity extends BaseTimeEntity {
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId; // 主键ID，使用雪花算法

    private String userAccount;
    private String password;
    private String nickName;
    private String avatar;
    private String email;
    private String phone;
    private String school;

    /**
     * 状态：0-禁用 1-正常
     */
    private Integer status;

    private Integer submittedCount;
    private Integer acceptedCount;
    private Integer rating;

    @TableLogic
    private Integer deleted; // 逻辑删除，0表示未删除，1表示已删除
}
