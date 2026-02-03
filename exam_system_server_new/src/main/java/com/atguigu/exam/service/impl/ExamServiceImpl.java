package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.vo.StartExamVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamMapper, Exam> implements ExamService {

    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
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

    @Override
    public ExamRecord customGetExamRecordById(Integer id) {
        //查询
        ExamRecord examRecord = examRecordMapper.selectById(id);
        if(examRecord==null){
            log.info("考试记录不存在");
            throw new RuntimeException("考试记录不存在");
        }
        //获取试卷信息
        Paper paper = paperService.getPaperById(examRecord.getExamId());
        examRecord.setPaper(paper);
        //获取答题记录
        LambdaQueryWrapper<AnswerRecord> answerRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        answerRecordLambdaQueryWrapper.eq(AnswerRecord::getExamRecordId, id);
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(answerRecordLambdaQueryWrapper);
        //将试卷id进行提取
        List<Long> questionIds = paper.getQuestions().stream().map(Question::getId).collect(Collectors.toList());
        //答题记录和试卷题目顺序一样，进行排序
        if(!ObjectUtils.isEmpty(answerRecords)){
            answerRecords.sort(
                    (o1, o2)->
                    {
                        int i = questionIds.indexOf(o1.getQuestionId());
                        int i1 = questionIds.indexOf(o2.getQuestionId());
                        return Integer.compare(i,i1);
                    }
            );
            examRecord.setAnswerRecords(answerRecords);
        }
        return examRecord;
    }
}