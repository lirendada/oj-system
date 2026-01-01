package com.liren.problem.vo;

import cn.hutool.core.bean.BeanUtil;
import com.liren.problem.entity.ProblemEntity;
import com.liren.problem.entity.ProblemTagEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "题目标签VO")
public class ProblemTagVO implements Serializable {

    @Schema(description = "标签ID")
    private Long tagId;

    @Schema(description = "标签名称")
    private String tagName;

    @Schema(description = "标签颜色")
    private String tagColor;

    /**
     * 静态转换方法
     */
    public static ProblemTagVO objToVo(ProblemTagEntity problemTagEntity) {
        if (problemTagEntity == null) {
            return null;
        }
        ProblemTagVO problemTagVO = new ProblemTagVO();
        BeanUtil.copyProperties(problemTagEntity, problemTagVO);
        return problemTagVO;
    }
}

