package com.liren.api.problem.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPasswordVersionDTO {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 密码版本号
     */
    private Long passwordVersion;
}
