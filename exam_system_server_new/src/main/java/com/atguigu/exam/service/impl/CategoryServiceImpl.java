package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService{

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 查询所有分类和分类下的数量
     * @return
     */
    @Override
    public List<Category> getAllCategories(){
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        //查询条件
        queryWrapper.orderByAsc(Category::getSort);
        //查询结果
        List<Category> list = categoryMapper.selectList(queryWrapper);
        List<Map<String, Long>> countList = categoryMapper.findCountList();

        //转化stream
        Map<Long, Long> collect = countList.stream().collect(Collectors.toMap(map -> map.get("category_id"), map -> map.get("count")));
        for (Category category : list)
        {
            category.setCount(collect.getOrDefault(category.getId(),0L));
        }
        return list;
    }
}