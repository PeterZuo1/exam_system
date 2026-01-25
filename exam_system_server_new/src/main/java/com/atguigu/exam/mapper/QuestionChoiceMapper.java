package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.QuestionChoice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目选项
 */
public interface QuestionChoiceMapper extends BaseMapper<QuestionChoice> {
    //查询方法
    @Select("select * from question_choices where is_deleted = 0 and question_id = #{questionId} order by sort asc ; ")
    List<QuestionChoice> findByQuestionId(Long questionId);
} 