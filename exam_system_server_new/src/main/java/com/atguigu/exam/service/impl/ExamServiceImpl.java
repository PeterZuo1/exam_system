package com.atguigu.exam.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.service.*;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    @Autowired
    private AnswerRecordService answerRecordService;
    @Autowired
    private KimiAiService kimiAiService;
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

    @Override
    public void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException {
        if (!ObjectUtils.isEmpty( answers)){
            List<AnswerRecord> collect = answers.stream()
                    .map(answer -> new AnswerRecord(examRecordId, answer.getQuestionId(), answer.getUserAnswer()))
                    .collect(Collectors.toList());
            //批量保存
            answerRecordService.saveBatch(collect);
        }
        ExamRecord examRecord = examRecordMapper.selectById(examRecordId);
        examRecord.setStatus("已完成");
        examRecord.setEndTime(LocalDateTime.now());
        //更新考试记录
        examRecordMapper.updateById(examRecord);
        //调用批卷方法
        graderExam(examRecordId);
    }

    /**
     * ai智能判卷
     * @param examRecordId
     * @return
     */
    @Override
    public ExamRecord graderExam(Integer examRecordId) throws InterruptedException {

        //宏观：  获取考试记录相关的信息（考试记录对象 考试记录答题记录 考试对应试卷）
        //  进行循环判断（1.答题记录进行修改 2.总体提到总分数 总正确数量）  修改考试记录（状态 -》 已批阅  修改 -》 总分数）   进行ai评语生成（总正确的题目数量）
        //  修改考试记录表  返回考试记录对象
        //1.获取考试记录和相关的信息（试卷和答题记录）
        ExamRecord examRecord = customGetExamRecordById(examRecordId);
        Paper paper = examRecord.getPaper();
        if(paper==null){
            log.info("考试记录对应的试卷不存在");
            examRecord.setStatus("已批阅");
            examRecord.setScore(0);
            examRecord.setAnswers("考试对应的试卷被删除！无法进行成绩判定！");
            examRecordMapper.updateById(examRecord);
            throw new RuntimeException("考试记录对应的试卷不存在");
        }
        //如果没有填写答题记录
        List<AnswerRecord> answerRecords = examRecord.getAnswerRecords();
        if(ObjectUtils.isEmpty(answerRecords)){
            log.info("考试对应的答题为空");
            examRecord.setStatus("已批阅");
            examRecord.setScore(0);
            examRecord.setAnswers("考试对应的答题为空！");
            examRecordMapper.updateById(examRecord);
            throw new RuntimeException("考试对应的答题为空");
        }
        //2.进行循环的判卷（1.记录总分数 2.记录正确题目数量 3. 修改每个答题记录的状态（得分，是否正确 0 1 2 ，text-》ai评语））
        int correctCont = 0;//正确数量
        int totalScore = 0;//总得分数
        //获取题目
        Map<Long, Question> collect = paper.getQuestions().stream().collect(Collectors.toMap(Question::getId, q -> q));
        for (AnswerRecord answerRecord : answerRecords){
            Question question = collect.get(Long.valueOf(answerRecord.getQuestionId()));
            if(question==null)
                continue;
            String userAnswer = answerRecord.getUserAnswer();//用户填写的答案
            String standardAnswer = question.getAnswer().getAnswer();//标准答案
            //判断题进行转换
            if(question.getType().equals("JUDGE")){
                userAnswer= normalizeJudgeAnswer(answerRecord.getUserAnswer());
            }
            try{
                //非简答题
                if (!question.getType().equals("TEXT")){
                    //正确
                    if(userAnswer.equals(standardAnswer)){
                        answerRecord.setIsCorrect(1);
                        answerRecord.setScore(question.getPaperScore().intValue());
                    }
                    //错误
                    else {
                        answerRecord.setIsCorrect(0);
                        answerRecord.setScore(0);
                    }
                }
                //简答题
                else{
                    //ai判卷，调用kimi
                    //生成判卷提示词
                    String prompt = kimiAiService.buildGradingPrompt(question, userAnswer, question.getPaperScore().intValue());
                    //调用kimi,进行交互
                    String result = kimiAiService.callKimiAi(prompt);
                    //结果转换json
                    JSONObject jsonObject = JSONObject.parseObject(result);
                    //ai给的分数
                    Integer score = jsonObject.getInteger("score");
                    if(score>=question.getPaperScore().intValue()){
                        answerRecord.setIsCorrect(1);
                        answerRecord.setScore(question.getPaperScore().intValue());//满分
                        answerRecord.setAiCorrection(jsonObject.getString("feedback"));
                    }
                    else if(score<=0){
                        //完全错误
                        answerRecord.setIsCorrect(0);
                        answerRecord.setScore(0);
                        answerRecord.setAiCorrection(jsonObject.getString("reason"));
                    }
                    else{
                        //部分得分
                        answerRecord.setIsCorrect(2);
                        answerRecord.setScore(score);
                        answerRecord.setAiCorrection(jsonObject.getString("reason"));
                    }
                }
                //计算总分
                totalScore+=answerRecord.getScore();
                if(answerRecord.getIsCorrect()==1){
                    //正确题目数量
                    correctCont++;
                }
            }catch (Exception e){
                answerRecord.setIsCorrect(0);
                answerRecord.setScore(0);
                answerRecord.setAiCorrection("判题过程出错！");
            }
        }
        //批量更新答题记录
        answerRecordService.updateBatchById(answerRecords);
        //ai评价,生成提示词
        String s = kimiAiService.buildSummaryPrompt(totalScore, paper.getTotalScore().intValue(), paper.getQuestionCount(), correctCont);
        //调用kimi
        String AiString = kimiAiService.callKimiAi(s);
        //赋值
        examRecord.setAnswers(AiString);
        //这张试卷的总分数
        examRecord.setScore(totalScore);
        examRecord.setStatus("已批阅");
        examRecordMapper.updateById(examRecord);
        return examRecord;
    }
    /**
     * 标准化判断题答案，将T/F转换为TRUE/FALSE
     * @param answer 原始答案
     * @return 标准化后的答案
     */
    private String normalizeJudgeAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return "";
        }

        String normalized = answer.trim().toUpperCase();
        switch (normalized) {
            case "T":
            case "TRUE":
            case "正确":
                return "TRUE";
            case "F":
            case "FALSE":
            case "错":
                return "FALSE";
            default:
                return normalized;
        }
    }
}