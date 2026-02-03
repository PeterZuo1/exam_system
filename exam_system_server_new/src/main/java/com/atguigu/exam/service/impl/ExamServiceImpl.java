package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Exam;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.mapper.ExamMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.vo.StartExamVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamMapper, Exam> implements ExamService {

    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Override
    public ExamRecord saveExam(StartExamVo startExamVo) {
        //判断是否正在考试，如果是直接返回
        LambdaQueryWrapper<ExamRecord> examRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        examRecordLambdaQueryWrapper.eq(ExamRecord::getExamId, startExamVo.getPaperId());
        examRecordLambdaQueryWrapper.eq(ExamRecord::getStudentName, startExamVo.getStudentName());
        examRecordLambdaQueryWrapper.eq(ExamRecord::getStatus, "进行中");
        ExamRecord examRecord1 = examRecordMapper.selectOne(examRecordLambdaQueryWrapper);
        if(examRecord1!=null){
            log.info("正在考试，请勿重复开始");
            return examRecord1;
        }
        ExamRecord examRecord = new ExamRecord();
        //赋值
        examRecord.setExamId(startExamVo.getPaperId());
        examRecord.setStudentName(startExamVo.getStudentName());
        examRecord.setStatus("进行中");
        examRecord.setScore(0);
        examRecord.setStartTime(LocalDateTime.now());
        examRecord.setWindowSwitches(0);
        //保存
        examRecordMapper.insert(examRecord);
        return examRecord;
    }
}