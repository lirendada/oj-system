package com.liren.contest.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.liren.common.core.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContestVO extends BaseEntity {

    private Long contestId;

    private String title;

    private String description;

    /**
     * 状态：0-未开始 1-进行中 2-已结束 (VO层动态计算)
     */
    private Integer status;

    private String statusDesc; // 状态描述文本

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 持续时间 (例如：2小时30分)
     */
    private String duration;
}