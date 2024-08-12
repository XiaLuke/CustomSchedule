package com.tf.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tf.base.DbEntity;
import com.tf.base.DbMapper;
import com.tf.util.CommonUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Equator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Configuration
public class ScheduleExecute {
    @Autowired
    private JobRefresh jobRefresh;
    @Autowired
    private DbMapper dbMapper;

    private List<DbEntity> lastEntities = new ArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);


    @Scheduled(cron = "0 0/30 * * * ?")
    public void execute() {
        QueryWrapper<DbEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("ip", CommonUtil.getLocalIp());
        List<DbEntity> entities = dbMapper.selectList(wrapper);

        // 比较此次查询的集合和上次集合数据是否一致，一致跳出后续方法，
        if (!lastEntities.isEmpty() && CollectionUtils.isEqualCollection(entities, lastEntities, new EntityEquator())) {
            return;
        }

        lastEntities = entities;

        // 等待 jobRefresh 配置完成
//        while (!jobRefresh.getConfigureComplicated()) {
//        }

        jobRefresh.refresh(entities);
    }

    /**
     * 使用 CountDownLatch 来替代 while 循环等待配置完成
     * @param event
     */
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        latch.countDown(); // 配置完成，解除等待
    }

    private static class EntityEquator implements Equator<DbEntity> {
        @Override
        public boolean equate(DbEntity lastItem, DbEntity newItem) {
            return lastItem.getIp().equals(newItem.getIp()) &&
                    lastItem.getClassPath().equals(newItem.getClassPath()) &&
                    lastItem.getJobName().equals(newItem.getJobName()) &&
                    lastItem.getUsedFlag().equals(newItem.getUsedFlag()) &&
                    lastItem.getCron().equals(newItem.getCron()) &&
                    lastItem.getRandomName().equals(newItem.getRandomName());
        }

        @Override
        public int hash(DbEntity newItem) {
            int value = newItem.getIp().hashCode();
            value = value * 31 + newItem.getClassPath().hashCode();
            value = value * 31 + newItem.getJobName().hashCode();
            value = value * 31 + newItem.getUsedFlag().hashCode();
            value = value * 31 + newItem.getCron().hashCode();
            value = value * 31 + newItem.getRandomName().hashCode();
            return value;
        }
    }
}
