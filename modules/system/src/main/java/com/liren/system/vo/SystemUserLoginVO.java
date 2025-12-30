package com.liren.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemUserLoginVO {
    private Long userId;
    private String userAccount;
    private String nickName;
}
