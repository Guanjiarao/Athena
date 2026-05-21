

package com.nageoffer.ai.ragent.triage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor used by TriageSupervisor to overlap Risk/Rule/Slot agent work after normalization.
 */
@Configuration
public class TriageAgentExecutorConfig {

    @Bean(name = "triageAgentExecutor")
    public TaskExecutor triageAgentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("triage-agent-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.initialize();
        return executor;
    }
}
