package com.liren.problem.vo;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.liren.problem.entity.ProblemEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "题目VO")
public class ProblemVO implements Serializable {
    // 必须转 String，防止前端精度丢失
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "题目ID")
    private Long problemId;

    @Schema(description = "题目标题")
    private String title;

    @Schema(description = "题目难度：1-简单 2-中等 3-困难")
    private Integer difficulty;

    @Schema(description = "标签列表")
    private List<ProblemTagVO> tags;

    @Schema(description = "提交数")
    private Integer submitNum;

    @Schema(description = "通过数")
    private Integer acceptedNum;

    @Schema(description = "状态：0-隐藏 1-正常")
    private Integer status;

    @Schema(description = "题目创建时间")
    private Date createTime;

    /**
     * 静态转换方法
     */
    public static ProblemVO objToVo(ProblemEntity problemEntity) {
        if (problemEntity == null) {
            return null;
        }
        ProblemVO problemVO = new ProblemVO();
        // BeanUtils 会自动匹配字段名：problemId -> problemId, title -> title
        BeanUtil.copyProperties(problemEntity, problemVO);
        return problemVO;
    }
}