package com.nebula.common.web.handler;

import com.nebula.common.core.domain.R;
import com.nebula.common.core.enums.ResultCode;
import com.nebula.common.core.exception.BizException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * 把所有 controller 抛出的异常统一转成 {@link R}，避免业务代码自己 try-catch
 *
 * @author nebula
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * 业务层主动抛出的可预期错误，如参数验证失败、权限不足等
     * 记录警告日志，返回自定义错误码和消息
     *
     * @param e 业务异常对象，包含错误码和错误消息
     * @return 返回带有错误码和消息的R失败响应
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理方法参数校验异常
     * 处理由@Valid注解触发的DTO校验失败，如@RequestBody参数校验
     * 收集所有字段错误信息并格式化返回
     *
     * @param e 方法参数校验异常对象
     * @return 返回参数无效的错误响应，包含具体字段错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // 通过Stream API收集所有字段错误信息，格式化为"字段名: 错误消息"的形式并用"; "连接
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 处理约束违反异常
     * 处理由@Validated注解触发的方法级参数校验失败，如@RequestParam上的约束
     * 收集所有约束违反的消息并返回
     *
     * @param e 约束违反异常对象
     * @return 返回参数无效的错误响应，包含违反约束的消息
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        // 通过Stream API收集所有约束违反的消息并用"; "连接
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 处理参数绑定异常
     * 处理表单参数或GET请求参数绑定失败的情况
     * 如参数类型不匹配、格式错误等
     *
     * @param e 参数绑定异常对象
     * @return 返回参数无效的错误响应，包含具体字段错误信息
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        // 收集所有字段错误信息并格式化返回
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return R.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 处理缺少请求参数异常
     * 当请求缺少必需的参数时抛出此异常
     *
     * @param e 缺少请求参数异常对象
     * @return 返回缺少参数的错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        // 返回缺少参数的错误响应，包含参数名信息
        return R.fail(ResultCode.BAD_REQUEST.getCode(), "缺少参数: " + e.getParameterName());
    }

    /**
     * 处理请求体不可读异常
     * 当请求体无法解析时抛出，通常是JSON格式错误或编码问题
     *
     * @param e 请求体不可读异常对象
     * @return 返回请求体格式错误的响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /**
     * 处理HTTP请求方法不支持异常
     * 当使用不正确的HTTP方法访问接口时抛出，如用POST访问仅支持GET的接口
     *
     * @param e HTTP请求方法不支持异常对象
     * @return 返回方法不被允许的错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ResultCode.METHOD_NOT_ALLOWED.getCode(), e.getMessage());
    }

    /**
     * 处理资源未找到异常
     * 当请求路径未匹配任何控制器映射时抛出
     * Spring 6+ 中替代了传统的404页面
     *
     * @param e 资源未找到异常对象
     * @return 返回资源未找到的错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 处理通用异常（兜底处理）
     * 捕获所有未被上述异常处理器处理的异常
     * 防止敏感的堆栈信息泄露到前端
     *
     * @param e 通用异常对象
     * @return 返回内部服务器错误的响应
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        // 记录完整的错误堆栈信息，便于排查问题
        log.error("系统异常", e);
        return R.fail(ResultCode.INTERNAL_SERVER_ERROR);
    }
}
