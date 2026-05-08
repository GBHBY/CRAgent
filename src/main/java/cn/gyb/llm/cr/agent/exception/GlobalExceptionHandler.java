package cn.gyb.llm.cr.agent.exception;

import cn.gyb.llm.cr.agent.common.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，捕获所有未处理的异常并返回统一的错误响应。
 * <p>
 * 使用 Spring RestControllerAdvice 机制，拦截控制器层抛出的异常，
 * 记录错误日志并返回格式化的错误信息。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有未捕获的异常。
     *
     * @param e 捕获到的异常
     * @return HTTP 500 响应，包含错误信息的 JSON
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(AgentResponse.error(e.getMessage()));
    }
}
