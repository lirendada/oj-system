package com.liren.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liren.common.core.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
