package com.liren.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_problem_tag")
public class ProblemTagEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "tag_id", type = IdType.ASSIGN_ID)
    private Long tagId;

    private String tagName; // 标签名称

    private String tagColor; // 标签颜色
}