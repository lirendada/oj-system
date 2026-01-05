package com.liren.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liren.problem.entity.ProblemTagRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemTagRelationMapper extends BaseMapper<ProblemTagRelationEntity> {
    void saveBatch(@Param("relationList") List<ProblemTagRelationEntity> relationList);
}
