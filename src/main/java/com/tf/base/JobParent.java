package com.tf.base;

public abstract class JobParent {
    private String logId;
    public String getLogId() {
        return logId;
    }
    public void setLogId(String logId) {
        this.logId = logId;
    }


    // 是否完成
    public boolean isComplicated = true;
    // cron表达式
    public abstract String getCron();
    // 任务名称
    public abstract String getJobName();
    public abstract String getRandomName();

    // 未结束就执行方法
    public void executeWithIsComplicated() throws Exception {
        if (!this.isComplicated) {
            return;
        }
        this.isComplicated = false;
        try {
            this.execute();
        } catch (Exception ex) {
            this.isComplicated = true;
            throw ex;
        }finally {
            this.isComplicated = true;
        }
    }

    public abstract void execute() throws Exception;


}
