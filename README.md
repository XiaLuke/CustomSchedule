在项目开发过程中，大多数程序员在面向多个定时任务时，通常会选择为每个任务单独创建一个对应的任务。这种方式在任务数量较少时似乎是一个简单有效的解决方案，只要任务能够按时顺利运行，问题似乎就解决了。然而，随着项目的扩展，定时任务的数量逐渐增加，管理这些任务变得愈发复杂和繁琐，尤其是在需要对任务进行统一监控、动态调整执行时间或添加新的任务时，手动管理的弊端就会逐渐显现出来。这不仅容易导致代码冗余，还可能引发任务调度冲突和系统资源的浪费。

为了应对这一挑战，我们开发了一套自定义定时任务调度功能，以简化任务管理并提高系统的可维护性和扩展性。

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

## **说明**

```
1. 页面支持动态修改
2. 支持修改定时任务的关键配置
3. 页面修改数据后实时更新任务配置
4. 利用Spring框架管理定时任务的实时更新
```

页面这个就自己去实现了，这里只说怎么实现刷新和调用

## **实现**

不同的任务要通过一个任务调度，可通过”泛型定义任务类型“，”统一任务接口和工厂模式“，”策略模式“，”Spring多任务调度“。这里采用泛型思想+模板方法。

### 泛型统一父类

```
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

定义一个抽象父类，允许不同子类继承并实现抽象方法。与泛型思想类似，但通过继承和方法重写保证任务多样性。

使用模板方法模式，定义通用业务流程（`executeWithIsComplicated()`），将实际业务逻辑交给子类执行（`execute()`方法）

### **子类继承**

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

子类实现getCron()来自定义运行时间，让任务调度更加灵活。

### **扫描注册**

```java
@Configuration
public class JobRegister implements ApplicationContextAware {
    @Autowired
    private ScheduleMapper scheduleService; // 数据库接口

    private static final ConcurrentHashMap<String, ExtendJob> jobMaps = new ConcurrentHashMap<>();
 
	 private static final Logger logger = LoggerFactory.getLogger(JobRegister.class);

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
		            logger.error("Failed to register job: {}", jobName, ex);
            }
        }
    }
}
```

实现`ApplicationContextAware` ,让Spring启动时扫描所有继承了父类的子类任务，并注册到`ConcurrentHashMap`中，再将这些任务插入数据库中。

- 获取当前任务类的全限定类名、方法名、任务名、随机标识符、cron表达式
- 调用jobRegister，将任务类名的对应任务实例映射到jobMaps中，在厚度调度任务使用。
- 注册成功后，调用`registerJobTaskToDb` 将任务信息入库

### 任务初始化

```java

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

    // 后续任务...
}
```

通过@Configuration 和 @EnableScheduling 被初始化。

Spring调用`configureTasks`方法，初始化调度器，并设置一个有N个线程的线程池。

调度器配置完成后，设置 `configureComplicated = true` 标识配置已完成。

### **等待配置完成**

暴露出来一个定时任务，按照给定规则去查询数据库中内容

将按照一定规则从数据库中查询出的数据与线程中的数据进行比较。这里是偷懒了，你也可以重写实体类中的 equals 和 hashCode 方法进行比较

```java
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
```

通过Spring，每10S触发一次execute。

execute先通过`CountDownLatch` 的 `await` 方法，等待配置完成（即 `configureComplicated` 变为 `true`）。

在 `ContextRefreshedEvent` 事件监听器中，当 Spring Context 初始化完成时，`latch.countDown()` 被调用，解除阻塞，表示可以开始任务调度。

### 任务调度执行

- execute被触发后，查询数据库中当前IP地址对应的任务列表
- 比较当前查询结果与上一次任务列表是否一致，若一致，跳过本次调度；否则
  更新 `lastEntities` 并调用 `jobRefresh.refresh(entities)` 方法。

### **任务刷新**

之前比较了两个list是否相等，相等时将不再进行线程中数据的更新以及后续操作。若不相等，则需要更新线程中的内容，更新后使用 CronTask 重新设置定时任务，在这个过程中需要过滤掉`未启动`，`没有cron表达式`的任务

```java
@Configuration
@EnableScheduling
public class JobRefresh implements SchedulingConfigurer {

		//前置操作...
		
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
```

`ScheduleJobRefresh` 的 `refresh` 方法通过同步锁确保任务注册和取消的原子性。

在 `refresh`方法中，调用 `deDuplicate` 方法移除数据库中已被删除的任务；对于有效的任务（`UsedFlag` 为 1，且有合法的 Cron 表达式），调用 `registerTask` 方法，将任务注册到调度器中；在 `registerTask` 方法中，会创建 `CronTask`，将任务的执行逻辑包装为 `Runnable`，并根据 Cron 表达式调度该任务。

### 任务执行

- 任务被调度后，当 Cron 表达式匹配的时间点到达时，调度器线程池会触发任务执行逻辑。
- 调度器调用`job.executeWithIsComplicated()` 方法执行任务：
    - 该方法首先检查任务是否已经在执行 (`complicated` 标志)。
    - 如果任务未在执行，则设置 `complicated = false`，然后调用子类的 `execute` 方法执行实际业务逻辑。
    - 执行完成后，`complicated` 标志复位为 `true`。
    - 执行过程中发生的任何异常都会捕获并重新抛出，确保任务的执行状态正确记录。

### 任务执行结果记录

- 任务执行后，无论成功或失败，都会在 `registerTask` 的 `Runnable` 逻辑中更新数据库记录（`finalExecuteTime`，`failReason` 等）。
- 如果任务执行失败，任务状态将被标记为 `UsedFlag = 0`，并记录失败原因。

到这里，这个可视化自定义控制定时任务的操作就完成了。在该实现中，服务启动时会自动注册和初始化所有定时任务，将任务信息持久化到数据库中。之后，调度器会定期检查任务配置的变化，并更新调度计划。当任务实际执行时，会调用注册好的任务逻辑，确保任务按计划执行，并记录执行结果。通过 `CountDownLatch` 和线程安全的集合，确保了任务调度的安全性和可靠性。

[完成代码参照这里](https://github.com/XiaLuke/CustomSchedule)

获取IP方法

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