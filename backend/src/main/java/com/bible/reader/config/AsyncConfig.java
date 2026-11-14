package com.bible.reader.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志异步线程池。队列满时直接丢弃并计数，绝不阻塞 Tomcat 线程。
 */
@Configuration
public class AsyncConfig {

    @Bean
    public CountingRejectedExecutionHandler logRejectionHandler() {
        return new CountingRejectedExecutionHandler();
    }

    @Bean(name = "logExecutor")
    public ThreadPoolTaskExecutor logExecutor(CountingRejectedExecutionHandler handler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("bible-log-");
        executor.setRejectedExecutionHandler(handler);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(3);
        executor.initialize();
        return executor;
    }

    public static class CountingRejectedExecutionHandler implements RejectedExecutionHandler {

        private static final Logger LOG = LoggerFactory.getLogger(CountingRejectedExecutionHandler.class);
        private static final long WARN_INTERVAL_MS = 30_000L;

        private final AtomicLong dropped = new AtomicLong();
        private final AtomicLong lastWarnAt = new AtomicLong();

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            long total = dropped.incrementAndGet();
            long now = System.currentTimeMillis();
            long last = lastWarnAt.get();
            if (now - last >= WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
                LOG.warn("日志异步队列已满，丢弃任务（累计丢弃 {} 条）", total);
            }
        }

        public long droppedCount() {
            return dropped.get();
        }
    }
}
