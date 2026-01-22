package com.atguigu.exam.mapper;

import com.atguigu.exam.entity.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT category_id,COUNT(1) as count FROM questions\n" +
            "GROUP BY category_id")
    List<Map<String,Long>> findCountList();
}