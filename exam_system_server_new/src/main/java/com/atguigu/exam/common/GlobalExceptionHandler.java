package com.atguigu.exam.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Title: GlobalExceptionHandler
 * @Author zuolizhi
 * @Package com.atguigu.exam.common
 * @Date 2026/1/22 16:40
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //定义异常
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception e) {
        e.printStackTrace();
        log.error("服务器异常：{}", e.getMessage());
        return Result.error(e.getMessage());
    }
}
