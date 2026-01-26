package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Override
    public void questionListPage(Page<Question> questionPage, QuestionQueryVo questionQueryVo) {
        questionMapper.selectPageByQuestionQueryVo(questionPage, questionQueryVo);
    }

    @Override
    public void questionListPageStream(Page<Question> questionPage, QuestionQueryVo questionQueryVo) {
        //模糊查询
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(questionQueryVo.getCategoryId()!=null, Question::getCategoryId, questionQueryVo.getCategoryId());
        queryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getDifficulty()), Question::getDifficulty, questionQueryVo.getDifficulty());
        queryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getType()), Question::getType, questionQueryVo.getType());
        queryWrapper.like(!ObjectUtils.isEmpty(questionQueryVo.getKeyword()), Question::getTitle, questionQueryVo.getKeyword());
        queryWrapper.orderByDesc(Question::getCreateTime);
        //分页查询
        page(questionPage, queryWrapper);
        if(ObjectUtils.isEmpty(questionPage.getRecords())){
            log.info("分页查询结果为空");
            return;
        }
        //获取题目ID列表
        List<Long> questinoIds = questionPage.getRecords().stream().map(Question::getId).collect(Collectors.toList());
        //查询所有选项
        LambdaQueryWrapper<QuestionChoice> choiceLambdaQueryWrapper = new LambdaQueryWrapper<>();
        choiceLambdaQueryWrapper.in(QuestionChoice::getQuestionId, questinoIds);
        List<QuestionChoice> questionChoices = questionChoiceMapper.selectList(choiceLambdaQueryWrapper);
        //查询所有答案
        LambdaQueryWrapper<QuestionAnswer> answerLambdaQueryWrapper = new LambdaQueryWrapper<>();
        answerLambdaQueryWrapper.in(QuestionAnswer::getQuestionId, questinoIds);
        List<QuestionAnswer> questionAnswers = questionAnswerMapper.selectList(answerLambdaQueryWrapper);
        //答案转换格式
        Map<Long, QuestionAnswer> longQuestionAnswerMap = questionAnswers.stream().collect(Collectors.toMap(QuestionAnswer::getQuestionId, c -> c));
        //题目选项转换格式
        Map<Long, List<QuestionChoice>> longQuestionChoiceMap = questionChoices.stream().collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        questionPage.getRecords().forEach(question -> {
            //赋值答案
            question.setAnswer(longQuestionAnswerMap.get(question.getId()));
            //赋值选项(只有选择题有选项)
            if("CHOICE".equals(question.getType())){
                //排序
                List<QuestionChoice> questionChoices1 = longQuestionChoiceMap.get(question.getId());
                if(!ObjectUtils.isNotEmpty(questionChoices1)) {
                    questionChoices1.sort(Comparator.comparing(QuestionChoice::getSort));
                    //赋值
                    question.setChoices(questionChoices1);
                }
            }
        });
    }
}