package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {


    @Autowired
    private PaperQuestionService paperQuestionService;
    @Transactional
    @Override
    public Paper createPaper(PaperVo paperVo) {
        Paper paper = new Paper();
        //赋值
        BeanUtils.copyProperties(paperVo, paper);
        paper.setStatus("DRAFT");//默认状态
        //如果没有题目
        if(paperVo.getQuestions().isEmpty()){
            //默认赋值为0
            paper.setTotalScore(BigDecimal.ZERO);
            paper.setQuestionCount(0);
            log.warn("试卷没有题目");
            save( paper);
            return paper;
        }
        //有题目，计算总分和题目数量
        paper.setQuestionCount(paperVo.getQuestions().size());
        Optional<BigDecimal> reduce = paperVo.getQuestions().values().stream().reduce(BigDecimal::add);
        paper.setTotalScore(reduce.get());
        log.info("创建试卷成功，试卷ID为{}", paper.getId());
        save(paper);
        //保存中间表
        List<PaperQuestion> collect = paperVo.getQuestions().entrySet().stream()
                .map(e -> new PaperQuestion(paper.getId().intValue(), Long.valueOf(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
        //批量保存
        paperQuestionService.saveBatch(collect);
        //返回
        return paper;
    }
}