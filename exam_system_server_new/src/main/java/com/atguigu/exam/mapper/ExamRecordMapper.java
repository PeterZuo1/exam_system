package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.vo.ExamRankingVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @description 针对表【exam_record(考试记录表)】的数据库操作Mapper
 * @createDate 2025-06-20 22:37:43
 * @Entity com.atguigu.exam.entity.ExamRecord
 */
@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    public IPage<ExamRecord> getPageExamRecords(IPage<ExamRecord> page,String studentName, String status, String startDate, String endDate);

    /**
     * 获取考试排行榜
     * @param paperId 试卷ID
     * @param limit 显示数量限制
     */
    List<ExamRankingVO> getExamRanking(@Param("paperId") Integer paperId, @Param("limit") Integer limit);
}