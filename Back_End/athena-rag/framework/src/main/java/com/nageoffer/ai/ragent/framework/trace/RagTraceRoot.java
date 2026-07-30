

package com.nageoffer.ai.ragent.framework.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 RAG Trace 根节点（一次完整请求）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RagTraceRoot {

    /**
     * 链路名称（用于展示）
     */
    String name() default "";

    /**
     * 会话 ID 参数名
     */
    String conversationIdArg() default "conversationId";

    /**
     * 任务 ID 参数名
     */
    String taskIdArg() default "taskId";

    /**
     * 从参数对象中读取会话 ID 的 getter 名称。
     *
     * <p>当入口方法只有 request 对象时，可配置 conversationIdArg="request"、conversationIdGetter="getSessionId"。
     * 如果直接使用字符串参数，保持默认空值即可。</p>
     */
    String conversationIdGetter() default "";

    /**
     * 从参数对象中读取任务 ID 的 getter 名称。
     */
    String taskIdGetter() default "";
}
