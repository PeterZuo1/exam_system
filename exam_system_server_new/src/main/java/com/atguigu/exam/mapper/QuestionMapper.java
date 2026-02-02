package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {

    //分页查询
    IPage<Question> selectPageByQuestionQueryVo(IPage<Question> page, @Param("questionQueryVo") QuestionQueryVo questionQueryVo);

    //查询试卷中包含的题目
    List<Question> selectQuestionByPaperId(Integer paperId);
} 