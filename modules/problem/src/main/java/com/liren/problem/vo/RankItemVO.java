package com.liren.problem.vo;

import lombok.Data;

@Data
public class RankItemVO {
    private Long userId;
    private String nickname;
    private String avatar;    // 用户头像
    private Integer acceptedCount; // 用户通过的题目数量
    // private Integer rank; // 排名序号
}