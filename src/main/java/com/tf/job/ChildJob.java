package com.tf.job;

import com.tf.base.JobParent;
import org.springframework.context.annotation.Configuration;

@Configuration
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
    public void execute() throws Exception {
        System.out.println("开始执行");
    }

}