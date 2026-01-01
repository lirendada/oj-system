package com.liren.problem.entity;

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
@TableName("tb_problem")
public class ProblemEntity extends BaseEntity {

    @TableId(value = "problem_id", type = IdType.ASSIGN_ID)
    private Long problemId;

    private String title;

    /**
     * 难度：1-简单 2-中等 3-困难
     */
    private Integer difficulty;

    private Integer submitNum; // 提交数

    private Integer acceptedNum; // 通过数

    private String description; // 题目描述

    private String inputDescription; // 输入描述

    private String outputDescription; // 输出描述

    private Integer timeLimit; // 时间限制ms

    private Integer memoryLimit; // 空间限制MB

    private Integer stackLimit; // 栈空间限制MB

    private String sampleInput; // 样例输入

    private String sampleOutput; // 样例输出

    private String hint; // 提示

    private String source; // 来源

    /**
     * 状态：0-隐藏 1-正常
     */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
