package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.ExamRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

/**
 * 考试记录Service实现类
 * 实现考试记录相关的业务逻辑
 */
@Service
@Slf4j
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {

    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Override
    public void pageList(Page<ExamRecord> examRecordPage, String studentName, Integer status, String startDate, String endDate) {
        String statusText = null;
        if (status != null) {
            switch (status) {
                case 0: statusText = "进行中"; break;
                case 1: statusText = "已完成"; break;
                case 2: statusText = "已批阅"; break;
            }
        }
        //查询
        IPage<ExamRecord> pageExamRecords = examRecordMapper.getPageExamRecords(examRecordPage, studentName, statusText, startDate, endDate);
        log.info("分页查询考试记录成功，结果：{}", pageExamRecords);
    }

    @Override
    @Transactional
    public void customRemoveById(Integer id) {
        ExamRecord examRecord = getById(id);
        if(examRecord==null){
            throw new RuntimeException("考试记录不存在");
        }
        if(!examRecord.getStatus().equals("已批阅")){
            throw new RuntimeException("考试记录未完成，不能删除");
        }
        //删除考试记录表
        removeById(id);
        //删除子表答题记录表
        answerRecordMapper.delete(new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, id));
    }
}