package com.tf.base;

import lombok.Data;

import java.util.Date;

@Data
public class DbEntity {
    private String randomName;
    private String jobName;
    private Integer usedFlag;
    private String cron;
    private String createPerson;
    private Date createDate;
    private String updatePerson;
    private Date updateDate;
    private String classPath;
    private String ip;
    private Date finalExecuteTime;
}
