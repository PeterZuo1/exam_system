package com.atguigu.exam.service;


import com.atguigu.exam.entity.Question;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;

import java.util.List;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {
    /**
     * 调用Kimi AI接口
     * @param prompt
     * @return
     */
    String callKimiAi(String prompt) throws InterruptedException;

    String buildPrompt(AiGenerateRequestVo request);

    List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) throws InterruptedException;

    /**
     * ai批卷生成提示词
     * @param question
     * @param userAnswer
     * @param maxScore
     * @return
     */
    String buildGradingPrompt(Question question, String userAnswer, Integer maxScore);

    /**
     * ai评语生成提示
     */
    String buildSummaryPrompt(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount);
}