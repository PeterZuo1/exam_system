package com.atguigu.exam.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * @Title: MybatisPlusConfiguration
 * @Author zuolizhi
 * @Package com.atguigu.exam.config
 * @Date 2026/1/21 21:57
 */
@Configuration
@MapperScan("com.atguigu.exam.mapper")
public class MybatisPlusConfiguration {

}
