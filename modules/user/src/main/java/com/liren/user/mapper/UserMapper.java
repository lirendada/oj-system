package com.liren.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liren.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    List<UserEntity> getBatchUser(@Param("userIds") List<Long> userIds);
}
