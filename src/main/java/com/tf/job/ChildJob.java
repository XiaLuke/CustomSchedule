package com.tf.job;

import com.tf.base.JobParent;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class ChildJob extends JobParent {

    @Override
    public String getCron() {
        return "0 0 2 * * ?";
    }

    @Override
    public String getJobName() {
        return "childJob";
    }

    @Override
    public String getRandomName() {
        return "子任务";
    }

    @Override
    @Async // 将子任务执行交给子线程，避免阻塞调度线程
    public void execute() throws Exception {
        System.out.println("开始执行");
    }

}