

package com.nageoffer.ai.ragent.rag.aop;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.framework.trace.RagTraceRoot;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * 注解式 RAG Trace 采集切面
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RagTraceAspect {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final RagTraceRecordService traceRecordService;
    private final RagTraceProperties traceProperties;

    @Around("@annotation(traceRoot)")
    public Object aroundRoot(ProceedingJoinPoint joinPoint, RagTraceRoot traceRoot) throws Throwable {
        if (!traceProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        String existingTraceId = RagTraceContext.getTraceId();
        if (StrUtil.isNotBlank(existingTraceId)) {
            // 当前线程已在链路中，避免重复创建 root
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String traceId = IdUtil.getSnowflakeNextIdStr();
        String conversationId = resolveStringArg(signature, joinPoint.getArgs(), traceRoot.conversationIdArg(), traceRoot.conversationIdGetter());
        String taskId = resolveStringArg(signature, joinPoint.getArgs(), traceRoot.taskIdArg(), traceRoot.taskIdGetter());
        String traceName = StrUtil.blankToDefault(traceRoot.name(), method.getName());
        Date startTime = new Date();
        long startMillis = System.currentTimeMillis();

        try {
            traceRecordService.startRun(RagTraceRunDO.builder()
                    .traceId(traceId)
                    .traceName(limitLength(traceName, 64))
                    .entryMethod(limitLength(method.getDeclaringClass().getName() + "#" + method.getName(), 255))
                    .conversationId(limitLength(conversationId, 20))
                    .taskId(limitLength(taskId, 64))
                    .userId(limitLength(UserContext.getUserId(), 64))
                    .status(limitLength(STATUS_RUNNING, 20))
                    .startTime(startTime)
                    .build());
        } catch (Exception traceEx) {
            log.warn("trace run start 失败，跳过本次 trace，traceName={}", traceName, traceEx);
            return joinPoint.proceed();
        }

        RagTraceContext.setTraceId(traceId);
        try {
            Object result = joinPoint.proceed();
            finishRunSafely(traceId, STATUS_SUCCESS, null, startMillis);
            return result;
        } catch (Throwable ex) {
            finishRunSafely(traceId, STATUS_ERROR, truncateError(ex), startMillis);
            throw ex;
        } finally {
            RagTraceContext.clear();
        }
    }

    @Around("@annotation(traceNode)")
    public Object aroundNode(ProceedingJoinPoint joinPoint, RagTraceNode traceNode) throws Throwable {
        if (!traceProperties.isEnabled()) {
            return joinPoint.proceed();
        }
        String traceId = RagTraceContext.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String nodeId = IdUtil.getSnowflakeNextIdStr();
        String parentNodeId = RagTraceContext.currentNodeId();
        int depth = RagTraceContext.depth();
        Date startTime = new Date();
        long startMillis = System.currentTimeMillis();

        try {
            traceRecordService.startNode(RagTraceNodeDO.builder()
                    .traceId(traceId)
                    .nodeId(nodeId)
                    .parentNodeId(parentNodeId)
                    .depth(depth)
                    .nodeType(limitLength(StrUtil.blankToDefault(traceNode.type(), "METHOD"), 16))
                    .nodeName(limitLength(StrUtil.blankToDefault(traceNode.name(), method.getName()), 128))
                    .className(limitLength(method.getDeclaringClass().getName(), 255))
                    .methodName(limitLength(method.getName(), 128))
                    .status(limitLength(STATUS_RUNNING, 20))
                    .startTime(startTime)
                    .build());
        } catch (Exception traceEx) {
            log.warn("trace node start 失败，跳过节点 trace，nodeName={}，nodeType={}", traceNode.name(), traceNode.type(), traceEx);
            return joinPoint.proceed();
        }

        RagTraceContext.pushNode(nodeId);
        try {
            Object result = joinPoint.proceed();
            finishNodeSafely(traceId, nodeId, STATUS_SUCCESS, null, startMillis);
            return result;
        } catch (Throwable ex) {
            finishNodeSafely(traceId, nodeId, STATUS_ERROR, truncateError(ex), startMillis);
            throw ex;
        } finally {
            RagTraceContext.popNodeIfCurrent(nodeId);
        }
    }

    private String resolveStringArg(MethodSignature signature, Object[] args, String argName, String getterName) {
        if (StrUtil.isBlank(argName) || args == null || args.length == 0) {
            return null;
        }
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames == null || parameterNames.length != args.length) {
            return null;
        }
        for (int i = 0; i < parameterNames.length; i++) {
            if (!argName.equals(parameterNames[i])) {
                continue;
            }
            Object arg = args[i];
            if (arg == null) {
                return null;
            }
            Object value = resolveGetterValue(arg, getterName);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private Object resolveGetterValue(Object arg, String getterName) {
        if (arg == null) {
            return null;
        }
        if (StrUtil.isBlank(getterName)) {
            return arg;
        }
        try {
            Method getter = arg.getClass().getMethod(getterName);
            return getter.invoke(arg);
        } catch (Exception ex) {
            log.debug("trace root 参数 getter 解析失败，class={}，getter={}", arg.getClass().getName(), getterName, ex);
            return null;
        }
    }

    private void finishRunSafely(String traceId, String status, String errorMessage, long startMillis) {
        try {
            traceRecordService.finishRun(
                    traceId,
                    limitLength(status, 20),
                    errorMessage,
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
        } catch (Exception traceEx) {
            log.warn("trace run finish 失败，traceId={}，status={}", traceId, status, traceEx);
        }
    }

    private void finishNodeSafely(String traceId, String nodeId, String status, String errorMessage, long startMillis) {
        try {
            traceRecordService.finishNode(
                    traceId,
                    nodeId,
                    status,
                    errorMessage,
                    new Date(),
                    System.currentTimeMillis() - startMillis
            );
        } catch (Exception traceEx) {
            log.warn("trace node finish 失败，traceId={}，nodeId={}，status={}", traceId, nodeId, status, traceEx);
        }
    }

    private String limitLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(throwable.getMessage(), "");
        if (message.length() <= traceProperties.getMaxErrorLength()) {
            return message;
        }
        return message.substring(0, traceProperties.getMaxErrorLength());
    }
}
