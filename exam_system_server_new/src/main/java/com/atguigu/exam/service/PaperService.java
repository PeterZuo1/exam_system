package com.atguigu.exam.service;

import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.vo.AiPaperVo;
import com.atguigu.exam.vo.PaperVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {

    /**
     * 创建试卷
     * @param paperVo 试卷信息
     * @return 创建的试卷对象
     */
    Paper createPaper(PaperVo paperVo);

    /**
     * 使用AI自动生成试卷
     * @param aiPaperVo 试卷信息
     * @return 创建的试卷对象
     */
    Paper createPaperWithAI(AiPaperVo aiPaperVo);

    /**
     * 更新试卷信息
     * @param id
     * @param paperVo
     * @return
     */
    Paper updatePaper(Integer id, PaperVo paperVo);

    /**
     * 删除试卷
     * @param id
     */
    void deletePaper(Integer id);
}