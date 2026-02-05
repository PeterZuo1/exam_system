package com.atguigu.exam.service;

import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;


import java.util.List;

/**
 * 考试记录Service接口
 * 定义考试记录相关的业务方法
 */
public interface ExamRecordService extends IService<ExamRecord> {


    /**
     * 分页查询考试记录
     *
     * @param examRecordPage 考试记录分页对象
     * @param studentName    学生姓名
     * @param status         考试状态
     * @param startDate      开始日期
     * @param endDate        结束日期
     */
    void pageList(Page<ExamRecord> examRecordPage, String studentName, Integer status, String startDate, String endDate);

    /**
     * 根据ID删除考试记录
     *
     * @param id 考试记录ID
     */
    void customRemoveById(Integer id);

    /**
     * 获取考试排行榜
     *
     * @param paperId  试卷ID
     * @param limit    显示数量限制
     * @return 考试排行榜列表
     */
    List<ExamRankingVO> getExamRanking(Integer paperId, Integer limit);
}