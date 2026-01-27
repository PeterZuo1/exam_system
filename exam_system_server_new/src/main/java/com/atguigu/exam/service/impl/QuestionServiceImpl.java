package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired
    private RedisUtils redisUtils;// Redis操作工具类
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

    @Override
    public Question getQuestionById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question== null){
            throw new RuntimeException("查询结果为空");
        }
        //查询所有选项
        LambdaQueryWrapper<QuestionChoice> choiceLambdaQueryWrapper = new LambdaQueryWrapper<>();
        choiceLambdaQueryWrapper.eq(QuestionChoice::getQuestionId, question.getId());
        //选项
        if (question.getType().equals("CHOICE")){
            List<QuestionChoice> questionChoices = questionChoiceMapper.selectList(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, question.getId()));
            //题目赋值选项
            question.setChoices(questionChoices);
        }
        //查询所有答案
        LambdaQueryWrapper<QuestionAnswer> answerLambdaQueryWrapper = new LambdaQueryWrapper<>();
        answerLambdaQueryWrapper.eq(QuestionAnswer::getQuestionId, question.getId());
        QuestionAnswer questionAnswer = questionAnswerMapper.selectOne(answerLambdaQueryWrapper);
        question.setAnswer(questionAnswer);
        //开启线程
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        })
        //省略写法
        new Thread(() -> {
            incrementQuestionScore(question.getId());
        }).start();
        return question;
    }

    /**
     * 创建题目
     * @param question
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Question createQuestion(Question question) {
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getTitle, question.getTitle());
        queryWrapper.eq(Question::getType, question.getType());
        long count = count(queryWrapper);
        if(count > 0){
            throw new RuntimeException("已存在相同标题的题目");
        }
        //保存问题
        save(question);
        QuestionAnswer questionAnswer = new QuestionAnswer();
        if(question.getType().equals("CHOICE")){
            List<QuestionChoice> choices = question.getChoices();
            StringBuilder stringBuilder = new StringBuilder();
            for (int i=0;i<choices.size();i++){
                QuestionChoice choice = choices.get(i);
                choice.setSort(i);
                choice.setQuestionId(question.getId());
                //保存选项
                questionChoiceMapper.insert(choice);
                //拼接答案
                if(choice.getIsCorrect()){
                    if(stringBuilder.length() > 0){
                        stringBuilder.append(",");
                    }
                    //拼接正确答案
                    stringBuilder.append((char) ('A'+i));
                }
            }
            questionAnswer.setAnswer(stringBuilder.toString());
            questionAnswer.setQuestionId(question.getId());
            questionAnswerMapper.insert(questionAnswer);
        }
        return question;
    }

    /**
     * 热门题目存储到Redis中
     * @param questionId
     * @return
     */
    private void incrementQuestionScore(Long questionId) {
        //调用redis自定义工具类
        redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,questionId, 1);
        log.info("题目 {} 浏览次数加1", questionId);
    }
}