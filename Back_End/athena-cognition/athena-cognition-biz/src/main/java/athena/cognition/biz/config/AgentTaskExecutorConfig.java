package athena.cognition.biz.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 认知图谱 Agent 任务异步执行线程池（照 athena-ground 的 NoteDetailExecutorConfig 先例：
 * CallerRunsPolicy + 优雅停机）。Agent 工作流是同步 HTTP 且耗时较长，
 * 任务先落库再由该线程池异步执行，Android 立即拿到 taskId（交接文档 section 11）。
 */
@Slf4j
@Configuration
public class AgentTaskExecutorConfig {

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cognition-agent-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
