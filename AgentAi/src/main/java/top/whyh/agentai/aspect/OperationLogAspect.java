package top.whyh.agentai.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.whyh.agentai.annotation.OperationLog;
import top.whyh.agentai.service.SystemLogService;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SystemLogService systemLogService;

    @Around("@annotation(top.whyh.agentai.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        OperationLog annotation = signature.getMethod().getAnnotation(OperationLog.class);

        Object result = point.proceed();

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attrs != null ? attrs.getRequest() : null;
            if (request != null) {
                systemLogService.saveOperationLog(
                        annotation.module(), annotation.type(), annotation.description(), request, result);
            }
        } catch (Exception e) {
            // 日志记录失败不影响业务
        }

        return result;
    }
}
