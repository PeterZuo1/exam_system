package com.atguigu.exam.service;

import com.atguigu.exam.entity.Exam;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.StartExamVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<Exam> {

    ExamRecord saveExam(StartExamVo startExamVo);
}
 