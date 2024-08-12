在项目开发过程中，向我们这些普通程序员，当出现多个定时任务时，第一时间想到的当然是每个任务创建一个任务。只要能顺利运行，感觉就结束了（普天之下，莫非王土）

但是单个定时任务还好管理，当任务数量多起来时，管理起来力不从心

当然，还有SpringBoot提供的Quartz。

# 什么是Quartz

如果定时任务没有分布式需求，但需要对人物有一定的动态管理，如任务启动、暂停、恢复、停止和出发时间修改。整个模块可分为三个部分

- Job：待定时执行的具体工作内容
- Trigger：触发器，指定运行参数，包括运行次数、运行开始时间和计数时间、运行时长
- Scheduler：调度器：将Job和Trigger组装起来，让定时任务真正执行

三者之间的关系为：

- 一个`JobDetail` 可以绑定多个 `Tigger`，但一个 `Trigger` 只能绑定一个`JobDetail`
- 每个`JobDetail` 和`Trigger`可通过`group`和`name`标识唯一性
- 一个`Schedule`可调度多个`JobDetai`和`Trigger`



# 自定义

额...我是说如果，你想自己实现，不想用框架自己实现哈（闲着没事干），下面我就说说我的自定义可视化任务吧

## 说明

```
1. 想要动态，就说要有页面支持修改吧
2. 修改什么？改cron表达式，改ip啊
3. 页面修改数据后，要实时更新吧
4. 实时更新，这一层就交给spring管理咯
```

页面这个就自己去实现了，这里只说怎么实现



## 实现

不同的任务要通过一个任务调度，要么使用泛型，那我就用父类

### 1定义统一父类

定义一个抽象父类，制定子类继承后重写的规范

```java
public abstract class ParentJob {
    public abstract String getCron(); // cron表达式
    public abstract String getJobName(); // 任务说明
    public abstract String getRandomName(); // 任务名
    public boolean isComplicated = true; // 是否完成
    private String logId;
    
    public void setLogId(String logId) {
        this.logId = logId;
    }
    
    // 实际调用业务
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
        }
        this.isComplicated = true;
    }
    
    public abstract void execute() throws Exception; // 子类调用任务
}
```





### 2.子类继承

子类继承抽象父类完善父类定义的各个方法

```java
@Configuration
public class ChildJob extends ParentJob {
    @Override
    public String getCron() {
        return "0 0 2 * * ?"; // 每个任务自定义运行时间
    }

    @Override
    public String getJobName() {
        return "子类定时任务";
    }

    @Override
    public String getRandomName() {
        return "childJob";
    }
	
    @Override
    public void execute() throws Exception {
        // 处理任务
    }
}
```



### 3.扫描注册

包括扫描到子类注册到spring的组件中以及将扫描的相关信息保存到数据库中



使用@Configuration标注，让子类能被扫描并注册在spring中，然后加一些提示呗

（子类已经注册到spring中了，但是spring中那么多组件）想要针对任务子类进行操作，得使用织入

上面说到需要修改ip，那就要[获取ip](#getIp)嘛。其中定义的`ConcurrentHashMap`和对Map的操作方法可抽取为单独的类进行定义和操作

```java
@Configuration
public class JobRegister implements ApplicationContextAware {
    @Autowired
    private ScheduleMapper scheduleService; // 数据库接口
    
    private static final ConcurrentHashMap<String, ExtendJob> jobMaps = new ConcurrentHashMap<>();
    
    public void jobRegister(String className, ExtendJob job) {
        jobMaps.put(className, job);
    }

    public ExtendJob getJob(String className) {
        return jobMaps.get(className);
    }
    
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
    	// 从spring中拿到继承了任务父类的类
        Map<String, ParentJob.class> map = context.getBeansOfType(ParentJob.class);
        // 获取IP
        String ip = CommonUtils.getLocalIp();
        
        map.forEach((name,job)) -> {
            String className = job.getClass().getName().split("\\$\\$")[0]; // 获取类名
            String jobName = job.getJobName(); // 获取任务名
            String autoId = job.getRandomName(); // 获取任务Id
            String cron = job.getCron();
            jobRegister(className, job); // 注册任务
            try {
                // 初次启动时将任务插入数据库中
                
                // 日志记录
            } catch (Exception ex) {
                // 日志记录
            }
        }
    }
}
```



### 4.主定时任务

暴露出来一个定时任务，按照给定规则去查询数据库中内容

将按照一定规则从数据库中查询出的数据与线程中的数据进行比较。这里是偷懒了，你也可以重写实体类中的 equals 和 hashCode 方法进行比较

```java
// 上次从数据库中拿到的数据与当前从数据库中的数据进行比较
if(!dbList.isEmpty() && CollectionUtils.isEqualCollection(dbList, rmList, new Equator<JobEntity>() {
    @Override
    public boolean equate(ScheduleEntity first, ScheduleEntity second) {
        return first.getIp().equals(second.getIp()) &&
            first.getClassPath().equals(second.getClassPath()) &&
            first.getJobName().equals(second.getJobName()) &&
            first.getUsedFlag().equals(second.getUsedFlag()) &&
            first.getCron().equals(second.getCron()) &&
            first.getRandomName().equals(second.getRandomName());
    }

    @Override
    public int hash(ScheduleEntity second) {
        int value = second.getIp().hashCode();
        value = value * 31 + second.getClassPath().hashCode();
        value = value * 31 + second.getJobName().hashCode();
        value = value * 31 + second.getUsedFlag().hashCode();
        value = value * 31 + second.getCron().hashCode();
        value = value * 31 + second.getRandomName().hashCode();
        return value;
    }
    // 更新线程中的内容
    lastList = entities;
}
```





### 5.任务刷新

之前比较了两个list是否相等，相等时将不再进行线程中数据的更新以及后续操作。若不相等，则需要更新线程中的内容，更新后使用 CronTask 重新设置定时任务，在这个过程中需要过滤掉`未启动`，`没有cron表达式`的任务

```java
// 从数据库中查询的数据（上层方法传递过来）

// 过滤数据，未启动，没有cron表达式的从线程任务中移除

private void extracted(ScheduleEntity entity, ExtendJob job) {
        CronTask cronTask = new CronTask(new Runnable() {
            @Override
            public void run() {
                try {
                    // 调用执行方法
                    job.executeWithIsComplicated();
                    // 更新数据库
                } catch (Exception e) {
                    // 更新数据库
                    throw new RuntimeException(e);
                }
            }
        }, entity.getCron()); // 使用从数据库中查询拿到的cron表达式

        ScheduledFuture<?> scheduledFuture = taskRegistrar.getScheduler().schedule(cronTask.getRunnable(), cronTask.getTrigger());
        cronTaskMap.put(job.getRandomName(), cronTask);
        scheduledFutureMap.put(job.getRandomName(), scheduledFuture);
    }
```





到这里，这个可视化自定义控制定时任务的操作就完成了，自己写出来的东西当然不可能和大团队开发的组件框架相比，能用就行。

[完成代码参照这里](https://github.com/XiaLuke/CustomSchedule)









<div id="getIp">获取IP方法</div>

```java
public class CommonUtils {
    public static String getLocalIp() {
        String ipAddress = "";
        try {
            InetAddress inetAddress = getLocalHostAddress();
            ipAddress = inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return ipAddress;
    }

    public static InetAddress getLocalHostAddress() throws UnknownHostException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress();
        } catch (SocketException e) {

        }

        //内网环境
        try {
            InetAddress candidateAddress = null;
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {

                        if (inetAddr.isSiteLocalAddress()) {
                            return inetAddr;
                        } else if (candidateAddress == null) {
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress;
            }
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        } catch (Exception e) {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}
```

