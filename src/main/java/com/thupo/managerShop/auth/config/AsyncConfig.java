package com.thupo.managerShop.auth.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor riêng cho các việc không được làm chậm luồng đăng nhập: gửi email, ghi audit, cập nhật last_login.
 * Hàng đợi có giới hạn; khi đầy sẽ bỏ việc và ghi cảnh báo thay vì chặn thread request.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String AUTH_TASK_EXECUTOR = "authTaskExecutor";

    @Bean(name = AUTH_TASK_EXECUTOR)
    public TaskExecutor authTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("auth-async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10_000);
        executor.setRejectedExecutionHandler((runnable, pool) -> {
            log.warn("Hàng đợi auth async đã đầy, bỏ một tác vụ nền");
            new ThreadPoolExecutor.DiscardPolicy().rejectedExecution(runnable, pool);
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
