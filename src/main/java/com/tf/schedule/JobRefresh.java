package com.tf.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tf.base.DbEntity;
import com.tf.base.DbMapper;
import com.tf.base.JobParent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

@Configuration
@EnableScheduling
public class JobRefresh implements SchedulingConfigurer {
    private ScheduledTaskRegistrar taskRegistrar;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutureMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CronTask> cronTaskMap = new ConcurrentHashMap<>();

    @Autowired
    private Register jobRegister;
    @Autowired
    private DbMapper dbMapper;

    private final Object lock = new Object();

    private boolean configureComplicated = false;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        this.taskRegistrar = registrar;
        registrar.setScheduler(Executors.newScheduledThreadPool(1));
        configureComplicated = true;
    }

    public void refresh(List<DbEntity> requestList) {
        synchronized (lock) {

            deDuplicate(requestList); // remove not exist task
            if (!requestList.isEmpty()) {
                for (DbEntity entity : requestList) {
                    try {
                        // 过滤未启动的任务, 0 为未启动, 1 为启动
                        if (entity.getUsedFlag() == 0) {
                            cancelExistingTask(entity);
                        }
                        // 跳过没有cron表达式的任务
                        if (StringUtils.isEmpty(entity.getCron())) {
                            continue;
                        }

                        // 根据全限定名获取类
                        JobParent job = jobRegister.getJob(entity.getClassPath());
                        if (job == null) {
                            continue;
                        }

                        // 移除已经存在的任务，重新添加，走到这里说明任务发生改变
                        cancelExistingTask(entity);
                        job.setLogId(entity.getRandomName());

                        extracted(entity, job);
                    } catch (Exception e) {
                        DbEntity recall = new DbEntity();
                        recall.setRandomName(entity.getRandomName());
                        recall.setFinalExecuteTime(new Date());
                        recall.setUsedFlag(0);
                        // scheduleMapper.updateById(DbEntity);
                    }
                }
            }
        }
    }

    private void extracted(DbEntity entity, JobParent job) {
        CronTask cronTask = new CronTask(() -> {
            try {
                job.executeWithIsComplicated();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            DbEntity scheduleEntity = new DbEntity();
            scheduleEntity.setRandomName(job.getRandomName());
            scheduleEntity.setFinalExecuteTime(new Date());
            dbMapper.update(scheduleEntity, new QueryWrapper<DbEntity>().eq("random_name", entity.getRandomName()));
        }, entity.getCron());

        ScheduledFuture<?> scheduledFuture = taskRegistrar.getScheduler().schedule(cronTask.getRunnable(), cronTask.getTrigger());
        cronTaskMap.put(job.getRandomName(), cronTask);
        scheduledFutureMap.put(job.getRandomName(), scheduledFuture);
    }

    // 移除已经删除的任务
    public void deDuplicate(List<DbEntity> requestList) {
        Set<String> taskKeyInMap = scheduledFutureMap.keySet();

        if (taskKeyInMap.isEmpty()) return;

        taskKeyInMap.forEach(key -> {
            if (!exists(requestList, key)) {
                scheduledFutureMap.get(key).cancel(false);
                scheduledFutureMap.remove(key);
                cronTaskMap.remove(key);
            }
        });
    }

    private boolean exists(List<DbEntity> taskList, String taskId) {
        return taskList.stream().anyMatch(task -> task.getRandomName().equals(taskId));
    }

    private void cancelExistingTask(DbEntity request) {
        ScheduledFuture<?> future = scheduledFutureMap.get(request.getRandomName());
        if (future != null) {
            future.cancel(false);
            scheduledFutureMap.remove(request.getRandomName());
            cronTaskMap.remove(request.getRandomName());
        }
    }

}
