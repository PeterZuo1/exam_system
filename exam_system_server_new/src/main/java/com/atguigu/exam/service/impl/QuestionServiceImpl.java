package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.PaperQuestionMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
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
    private PaperQuestionMapper paperQuestionMapper;
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
        addChoiceAndAnswer(questionPage.getRecords());
    }

    //提取公共赋值方法
    private void addChoiceAndAnswer(List<Question> questions) {
        //获取题目ID列表
        List<Long> questinoIds = questions.stream().map(Question::getId).collect(Collectors.toList());
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
        questions.forEach(question -> {
            //赋值答案
            question.setAnswer(longQuestionAnswerMap.get(question.getId()));
            //赋值选项(只有选择题有选项)
            if("CHOICE".equals(question.getType())){
                //排序
                List<QuestionChoice> questionChoices1 = longQuestionChoiceMap.get(question.getId());
                if(!ObjectUtils.isEmpty(questionChoices1)) {
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
     * 热门题目存储到Redis中
     * @param questionId
     * @return
     */
    private void incrementQuestionScore(Long questionId) {
        //调用redis自定义工具类
        redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,questionId, 1);
        log.info("题目 {} 浏览次数加1", questionId);
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
     * 更新所有
     * @param question
     */
    @Transactional
    @Override
    public void updateAll(Question question) {
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getTitle, question.getTitle());
        queryWrapper.eq(Question::getType, question.getType());
        long count = count(queryWrapper);
        if(count > 0){
            throw new RuntimeException("已存在相同标题的题目");
        }
        //更新问题
        updateById(question);
        if(question.getType().equals("CHOICE")){
            QuestionAnswer questionAnswer = new QuestionAnswer();
            List<QuestionChoice> choices = question.getChoices();
            //删除原来的选项
            questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, question.getId()));
            //存放答案
            StringBuilder stringBuilder = new StringBuilder();
            for(int i=0;i<choices.size();i++){
                QuestionChoice questionChoice = choices.get(i);
                questionChoice.setSort(i);
                if(questionChoice.getIsCorrect()){
                    if(stringBuilder.length()>0)
                        stringBuilder.append(",");
                    stringBuilder.append(questionChoice.getContent());
                }
                questionAnswer.setAnswer(stringBuilder.toString());
                questionAnswer.setQuestionId(question.getId());
                //更新答案
                questionAnswerMapper.updateById(questionAnswer);
                //新增选项
                questionChoiceMapper.insert(questionChoice);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void removeQuestion(Long id) {
        //判断是否题目在试卷中
        LambdaQueryWrapper<PaperQuestion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaperQuestion::getQuestionId,id);
        Long count = paperQuestionMapper.selectCount(queryWrapper);
        if (count > 0){
            throw new RuntimeException("该题目：%s 被试卷表中引用%s次，删除失败！".formatted(id,count));
        }
        //删除答案
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, id));
        //删除选项
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, id));
        //删除问题
        questionMapper.deleteById(id);
    }

    @Override
    public List<Question> customFindPopularQuestions(Integer size) {
        ArrayList<Question> questions = new ArrayList<>();
        //查询redis中的热门题目
        Set<Object> qs = redisUtils.zReverseRange(CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);
        if(!qs.isEmpty()){
            List<Long> collect = qs.stream().map(q -> Long.valueOf(q.toString())).collect(Collectors.toList());
            for (Long questionId : collect){
                Question question = questionMapper.selectById(questionId);
                if(question != null)
                    questions.add(question);
            }
        }
        //剩余热门题目数量
        int diff=size-questions.size();
        if(diff>0){
            LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.orderByDesc(Question::getCreateTime);
            //查询已有热门id
            List<Long> questionIds = questions.stream().map(Question::getId).collect(Collectors.toList());
            //不为空，排除已有的
            if(!ObjectUtils.isEmpty(questionIds)){
                queryWrapper.notIn(Question::getId, questionIds);
            }
            //切割
            queryWrapper.last("limit "+diff);
            List<Question> questions1 = questionMapper.selectList(queryWrapper);
            //拼接
            questions.addAll(questions1);
        }
        addChoiceAndAnswer(questions);
        return questions;
    }

    /**
     * 预览导入的Excel文件
     * @param file
     * @return
     */
    @Override
    public List<QuestionImportVo> previewExcel(MultipartFile file) throws IOException {
        if(file.isEmpty()){
            throw new RuntimeException("文件为空");
        }
        //获取文件名称
        String filename = file.getOriginalFilename();
        if(!filename.endsWith(".xlsx")&&!filename.endsWith(".xls")){
            throw new RuntimeException("文件格式错误");
        }
        //工具类预览文件
        List<QuestionImportVo> questionImportVos = ExcelUtil.parseExcel(file);
        return questionImportVos;
    }

    /**
     * 批量导入题目
     * @param questions
     * @return
     */
    @Override
    public String importQuestions(List<QuestionImportVo> questions) {
        //判断题目是否为空
        if(questions.isEmpty()){
            return "题目为空!";
        }
        //导入成功的题目数量
        int successCount = 0;
        for(QuestionImportVo questionImportVo : questions){
            try{
                Question question = new Question();
                //赋值相同属性
                BeanUtils.copyProperties(questionImportVo, question);
                //如果是选择题，添加选项
                if("CHOICE".equals(question.getType())){
                    //赋值
                    ArrayList<QuestionChoice> questionChoices = new ArrayList<>(questionImportVo.getChoices().size());
                    for(QuestionImportVo.ChoiceImportDto choiceImportDto : questionImportVo.getChoices()){
                        QuestionChoice questionChoice = new QuestionChoice();
                        questionChoice.setContent(choiceImportDto.getContent());
                        questionChoice.setSort(choiceImportDto.getSort());
                        questionChoice.setIsCorrect(choiceImportDto.getIsCorrect());
                        questionChoices.add(questionChoice);
                    }
                    question.setChoices(questionChoices);
                }
                //添加答案
                QuestionAnswer questionAnswer = new QuestionAnswer();
                //判断题，需要将true和false转成大写！ 否则无法识别！！
                if ("JUDGE".equals(questionImportVo.getType())){
                    questionAnswer.setAnswer(questionImportVo.getAnswer().toUpperCase());
                }else{
                    questionAnswer.setAnswer(questionImportVo.getAnswer());
                }
                questionAnswer.setKeywords(questionImportVo.getKeywords());
                question.setAnswer(questionAnswer);
                //执行保存题目
                createQuestion(question);
                //增加成功保存
                successCount++;
            }
            catch (Exception e){
                log.error("导入题目失败：{}", questionImportVo.getTitle());
            }
        }
        String result = "批量导入题目接口调用成功！ 一共：%s 题目需要导入，成功导入了：%s 道题！".formatted(questions.size(),successCount);
        return result;
    }

}