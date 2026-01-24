package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService{

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private QuestionMapper questionMapper;
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

    @Override
    public List<Category> getAllTreeCategories() {
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
        //构建树结构
        Map<Long, List<Category>> map = list.stream().collect(Collectors.groupingBy(Category::getParentId));
        //stream流筛选
        List<Category> list1 = list.stream().filter(c -> c.getParentId() == 0).collect(Collectors.toList());
        for (Category category : list1)
        {
            List<Category> lists = map.getOrDefault(category.getId(), new ArrayList<>());
            category.setChildren(lists);
            Long collect1 = lists.stream().collect(Collectors.summingLong(Category::getCount));
            category.setCount(category.getCount() +collect1);
        }
        return list1;
    }

    @Override
    public void addCategory(Category category) {
        //判断同级分类下已存在相同名称的分类
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId,category.getParentId());
        queryWrapper.eq(Category::getName,category.getName());
        //查询结果
        long count = count(queryWrapper);
        if (count > 0) {
            throw new RuntimeException("同级分类下已存在相同名称的分类");
        }
        save(category);
    }

    @Override
    public void updateCategory(Category category) {
        //1.先校验  同一父分类下！ 可以跟自己的name重复，不能跟其他的子分类name重复！
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Category::getParentId, category.getParentId()); // 同一父分类下！
        lambdaQueryWrapper.ne(Category::getId, category.getId());
        lambdaQueryWrapper.eq(Category::getName, category.getName());
        CategoryMapper categoryMapper = getBaseMapper();
        boolean exists = categoryMapper.exists(lambdaQueryWrapper);
        if (exists) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次更新失败！".formatted(parent.getName(),category.getName()));
        }
        //2.再更新
        updateById(category);
    }

    @Override
    public void deleteCategory(Long id) {
        //1.检查是否一级标题
        Category category = getById(id);
        if (category.getParentId() == 0){
            throw new RuntimeException("不能删除一级标题！");
        }
        //2.检查是否存在关联的题目
        LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Question::getCategoryId,id);
        long count = questionMapper.selectCount(lambdaQueryWrapper);
        if (count>0){
            throw new RuntimeException("当前的:%s分类，关联了%s道题目,无法删除！".formatted(category.getName(),count));
        }
        //3.以上不都不满足，删除即可【子关联数据，一并删除】
        removeById(id);
    }
}