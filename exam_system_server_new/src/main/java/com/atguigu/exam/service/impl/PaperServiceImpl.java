package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.PaperMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.PaperQuestionService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.atguigu.exam.vo.RuleVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
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
    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private ExamRecordMapper examRecordMapper;
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

    /**
     * 创建试卷（AI生成）
     * @param aiPaperVo
     * @return
     */
    @Override
    public Paper createPaperWithAI(AiPaperVo aiPaperVo) {
        Paper paper = new Paper();
        BeanUtils.copyProperties(aiPaperVo, paper);
        paper.setStatus("DRAFT");
        save(paper);
        //遍历规则
        log.info("创建试卷成功，试卷ID为{}", paper.getId());
        //2. 组卷规则下的试题选择和中间表的保存
        int questionCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        for (RuleVo rule : aiPaperVo.getRules()) {
            //校验规则
            if(rule.getCount()==0){
                log.debug("规则数量为0，忽略");
                continue;
            }
            //查询符合条件
            LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Question::getType, rule.getType());
            //判断是否有类别
            queryWrapper.in(!ObjectUtils.isEmpty(rule.getCategoryIds()),Question::getCategoryId,rule.getCategoryIds());
            List<Question> questions = questionMapper.selectList(queryWrapper);
            //校验试题数量
            if(questions.isEmpty()){
                log.debug("没有符合规则的试题");
                continue;
            }
            //校验选择数字小的进行筛选
            int realNumber = Math.min(rule.getCount(), questions.size());
            questionCount+=realNumber;
            //计算分数
            totalScore = totalScore.add(BigDecimal.valueOf((long) realNumber * rule.getScore()));
            //随机打乱题目
            Collections.shuffle(questions);
            //选择对应数量
            List<Question> subList = questions.subList(0, realNumber);
            //转换成中间表的集合
            List<PaperQuestion> collect = subList.stream().map(q -> new PaperQuestion(paper.getId().intValue(), q.getId(),
                    BigDecimal.valueOf(rule.getScore()))).collect(Collectors.toList());
            //批量保存中间表
            paperQuestionService.saveBatch(collect);
        }
        paper.setQuestionCount(questionCount);
        paper.setTotalScore(totalScore);
        //更新试卷参数
        updateById(paper);
        return paper;
    }

    @Transactional
    @Override
    public Paper updatePaper(Integer id, PaperVo paperVo) {
        Paper paper = getById(id);
        //校验试卷状态
        if(paper.getStatus().equals("PUBLISHED")){
            throw new RuntimeException("试卷已发布，不能修改");
        }
        //修改名字校验
        LambdaQueryWrapper<Paper> paperLambdaQueryWrapper = new LambdaQueryWrapper<>();
        paperLambdaQueryWrapper.eq(Paper::getName, paperVo.getName());
        paperLambdaQueryWrapper.ne(Paper::getId, id);
        long count = count(paperLambdaQueryWrapper);
        if(count>0){
            throw new RuntimeException("试卷名称已存在");
        }
        //赋值
        BeanUtils.copyProperties(paperVo, paper);
        //计算总分和题目数量
        paper.setQuestionCount(paperVo.getQuestions().size());
        Optional<BigDecimal> reduce = paperVo.getQuestions().values().stream().reduce(BigDecimal::add);
        paper.setTotalScore(reduce.get());
        //更新
        updateById(paper);
        //更新中间表，先删除在添加
        paperQuestionService.remove(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
        List<PaperQuestion> paperQuestions = paperVo.getQuestions().entrySet().stream()
                .map(e -> new PaperQuestion(id, Long.valueOf(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
        paperQuestionService.saveBatch(paperQuestions);
        return paper;
    }

    @Override
    public void deletePaper(Integer id) {
        Paper paper = getById(id);
        if(paper.getStatus().equals("PUBLISHED")){
            throw new RuntimeException("试卷已发布，不能删除");
        }
        Long count = examRecordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, id));
        //有考试记录
        if(count>0){
            throw new RuntimeException("试卷已考过，不能删除");
        }
        //删除试卷
        removeById(id);
        //删除中间表
        paperQuestionService.remove(new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, id));
    }

    @Override
    public Paper getPaperById(Integer id) {
        Paper paper = getById(id);
        //查询题目
        List<Question> questions = questionMapper.selectQuestionByPaperId(id);
        //进行题目排序,选择题，填空题，简答题
        questions.sort((o1, o2) -> Integer.compare(toScore(o1.getType()),toScore(o2.getType())));
        paper.setQuestions(questions);
        return paper;
    }
    private int toScore(String type){
        switch (type) {
            case "CHOICE":
                return 0;
            case "JUDGE":
                return 1;
            case "TEXT":
                return 2;
            default:
                return 3;
        }
    }
}