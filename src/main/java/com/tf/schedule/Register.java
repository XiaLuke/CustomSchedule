package com.tf.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tf.base.DbEntity;
import com.tf.base.DbMapper;
import com.tf.base.JobParent;
import com.tf.util.CommonUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class Register implements ApplicationContextAware {
    @Autowired
    private DbMapper dbMapper;
    private static final ConcurrentHashMap<String, JobParent> jobMaps = new ConcurrentHashMap<>();


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 获取所有继承了ExtendJob的类
        Map<String, JobParent> jobMaps = applicationContext.getBeansOfType(JobParent.class);
        String current_ip = CommonUtil.getLocalIp();

        jobMaps.forEach((name, job) -> {
            String className = job.getClass().getName().split("\\$\\$")[0]; // 获取类名
            String methodName = "execute"; // 获取方法名
            String jobName = job.getJobName(); // 获取任务名
            String autoId = job.getRandomName(); // 获取任务Id
            String cron = job.getCron();
            jobRegister(className, job); // 注册任务
            try {
                registerJobTaskToDb(autoId, cron, current_ip, jobName, className, methodName); // 初次启动时将任务插入数据库中
            } catch (Exception ex) {
                // 注册失败
            }
        });
    }

    public void jobRegister(String className, JobParent job) {
        jobMaps.put(className, job);
    }

    public JobParent getJob(String className) {
        return jobMaps.get(className);
    }

    private void registerJobTaskToDb(String autoId, String cron, String ip, String jobName, String className, String methodName) {
        DbEntity scheduleLogEntity = new DbEntity();
        scheduleLogEntity.setClassPath(className);

        QueryWrapper<DbEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("class_path", className);
        DbEntity queryOne = dbMapper.selectOne(wrapper);
        try {
            if (queryOne == null) {
                queryOne = new DbEntity();

                if (!autoId.isEmpty()) {
                    queryOne.setRandomName(autoId);
                }
                queryOne.setIp(ip);
                queryOne.setJobName(jobName);
                queryOne.setClassPath(className);
                queryOne.setCreatePerson("sys");
                queryOne.setUpdatePerson("sys");
                queryOne.setCron(cron);
                dbMapper.insert(queryOne);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
