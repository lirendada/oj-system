package com.liren.contest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.liren.common.core.base.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("tb_contest")
public class ContestEntity extends BaseEntity {

    @TableId(value = "contest_id", type = IdType.ASSIGN_ID)
    private Long contestId;

    private String title; // 竞赛标题

    private String description; // 竞赛描述

    /**
     * 状态：0-未开始 1-进行中 2-已结束
     */
    private Integer status;

    private LocalDateTime startTime; // 开始时间

    private LocalDateTime endTime; // 结束时间

    @TableLogic
    private Integer deleted;
}