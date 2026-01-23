package com.liren.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.liren.common.core.base.BaseEntity;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("tb_sys_user")
public class SystemUserEntity extends BaseEntity {
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId; // 主键ID，使用雪花算法

    private String userAccount;
    private String password;
    private String nickName;

    /**
     * 密码版本号，每次重置密码时递增，用于使旧 token 失效
     */
    private Long passwordVersion;

    @TableLogic
    private Integer deleted; // 逻辑删除，0表示未删除，1表示已删除
}