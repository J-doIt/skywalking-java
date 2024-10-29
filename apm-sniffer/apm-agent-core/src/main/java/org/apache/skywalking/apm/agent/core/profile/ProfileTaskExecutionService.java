/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.profile;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingThreadListener;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.constants.ProfileConstants;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * Profile task executor, use {@link #addProfileTask(ProfileTask)} to add a new profile task.
 * <pre>
 * (分析任务执行器，使用 addProfileTask(ProfileTask) 来添加新的分析任务。)
 * </pre>
 */
@DefaultImplementor
public class ProfileTaskExecutionService implements BootService, TracingThreadListener {

    private static final ILog LOGGER = LogManager.getLogger(ProfileTaskExecutionService.class);

    // add a schedule while waiting for the task to start or finish
    /**
     * 定时任务执行器：
     * 在 this.addProfileTask() 中制定 {@link #processProfileTask(ProfileTask)} 的执行计划。
     *      又在 {@link #processProfileTask(ProfileTask)} 中制定 {@link #stopCurrentProfileTask(ProfileTaskExecutionContext)} 的执行计划。
     */
    private final static ScheduledExecutorService PROFILE_TASK_SCHEDULE = Executors.newSingleThreadScheduledExecutor(
        new DefaultNamedThreadFactory("PROFILE-TASK-SCHEDULE"));

    // last command create time, use to next query task list
    /** 最后一个命令的创建时间，用于 下一个 查询任务列表 */
    private volatile long lastCommandCreateTime = -1;

    // current processing profile task context
    /**
     * 当前处理分析任务的上下文
     * this.profileTaskList 中的 ProfileTask 只能一个一个的分析，上一个分析完成，下一个才能开始分析。
     */
    private final AtomicReference<ProfileTaskExecutionContext> taskExecutionContext = new AtomicReference<>();

    // profile executor thread pool, only running one thread
    /** ProfileTask 执行器 */
    private final static ExecutorService PROFILE_EXECUTOR = Executors.newSingleThreadExecutor(
        new DefaultNamedThreadFactory("PROFILING-TASK"));

    // profile task list, include running and waiting running tasks
    /** 存放 ProfileTask 同步List */
    private final List<ProfileTask> profileTaskList = Collections.synchronizedList(new LinkedList<>());

    /**
     * add profile task from OAP
     */
    public void addProfileTask(ProfileTask task) {
        // update last command create time
        // 如果 该task 的 创建时间 > lastCommandCreateTime，则 更新 lastCommandCreateTime
        if (task.getCreateTime() > lastCommandCreateTime) {
            lastCommandCreateTime = task.getCreateTime();
        }

        // check profile task limit
        final CheckResult dataError = checkProfileTaskSuccess(task);
        if (!dataError.isSuccess()) {
            LOGGER.warn(
                "check command error, cannot process this profile task. reason: {}", dataError.getErrorReason());
            return;
        }

        // add task to list
        profileTaskList.add(task);

        // schedule to start task
        long timeToProcessMills = task.getStartTime() - System.currentTimeMillis();
        // 将 task 交由 执行器 执行处理步骤
        PROFILE_TASK_SCHEDULE.schedule(() -> processProfileTask(task), timeToProcessMills, TimeUnit.MILLISECONDS);
    }

    /**
     * check and add {@link TracingContext} profiling
     * <pre>
     * (检查，并添加 TracingContext 分析)
     * </pre>
     */
    public ProfileStatusContext addProfiling(TracingContext tracingContext,
                                             String traceSegmentId,
                                             String firstSpanOPName) {
        // get current profiling task, check need profiling
        // （获取当前的分析任务（profiling task），检查是否需要分析）
        final ProfileTaskExecutionContext executionContext = taskExecutionContext.get();
        // 如果 当前的 ProfileTaskExecutionContext 为空，则返回一个 ProfileStatus 为 NONE 的 ProfileStatusContext
                // 当 ProfileTaskChannelService 查询OAP发现有当前TracingContext的分析任务时，当前的 ProfileTaskExecutionContext 会被 ProfileTaskCommandExecutor 间接 set。
        if (executionContext == null) {
            return ProfileStatusContext.createWithNone();
        }

        // 尝试分析
        return executionContext.attemptProfiling(tracingContext, traceSegmentId, firstSpanOPName);
    }

    /**
     * continue profiling task when cross-thread
     * <pre>
     * (跨线程时继续采样任务)
     * </pre>
     */
    public void continueProfiling(TracingContext tracingContext, String traceSegmentId) {
        final ProfileTaskExecutionContext executionContext = taskExecutionContext.get();
        if (executionContext == null) {
            return;
        }

        executionContext.continueProfiling(tracingContext, traceSegmentId);
    }

    /**
     * Re-check current trace need profiling, in case that third-party plugins change the operation name.
     */
    public void profilingRecheck(TracingContext tracingContext, String traceSegmentId, String firstSpanOPName) {
        // get current profiling task, check need profiling
        final ProfileTaskExecutionContext executionContext = taskExecutionContext.get();
        if (executionContext == null) {
            return;
        }

        // 重新检查 分析任务
        executionContext.profilingRecheck(tracingContext, traceSegmentId, firstSpanOPName);
    }

    /**
     * active the selected profile task to execution task, and start a removal task for it.
     * <pre>
     * (将选定的 ProfileTask 激活到 执行任务，然后为其启动 Removal 任务。)
     *
     * 1. 停止 上一个 分析任务上下文
     * 2. 创建 当前ProfileTask 的 分析任务上下文
     * 3. 将 当前的ProfileTask 交给 this.PROFILE_EXECUTOR 执行
     * 4. 制定 定制 当前分析任务上下文 的执行计划
     * </pre>
     */
    private synchronized void processProfileTask(ProfileTask task) {
        // make sure prev profile task already stopped
        // (确保 上一个 分析任务 已停止)
        stopCurrentProfileTask(taskExecutionContext.get());

        // make stop task schedule and task context
        // (创建 Stop Task Schedule 和 Task 上下文)
        final ProfileTaskExecutionContext currentStartedTaskContext = new ProfileTaskExecutionContext(task);
        taskExecutionContext.set(currentStartedTaskContext);

        // start profiling this task
        // (开始分析此任务)
        currentStartedTaskContext.startProfiling(PROFILE_EXECUTOR);

        // task.getDuration 分钟后，停止 当前的 ProfileTaskExecutionContext
        PROFILE_TASK_SCHEDULE.schedule(
            () -> stopCurrentProfileTask(currentStartedTaskContext), task.getDuration(), TimeUnit.MINUTES);
    }

    /**
     * stop profile task, remove context data
     * <pre>
     * (停止 分析任务，删除上下文数据)
     * </pre>
     */
    synchronized void stopCurrentProfileTask(ProfileTaskExecutionContext needToStop) {
        // stop same context only
        // taskExecutionContext 的 引用 置为 null
        if (needToStop == null || !taskExecutionContext.compareAndSet(needToStop, null)) {
            return;
        }

        // current execution stop running
        // 停止 当前的 ProfileTaskExecutionContext
        needToStop.stopProfiling();

        // remove task
        // 移除 当前的 ProfileTask
        profileTaskList.remove(needToStop.getTask());

        // notify profiling task has finished
        // 通知 当前的 ProfileTask 已完成
        ServiceManager.INSTANCE.findService(ProfileTaskChannelService.class)
                               .notifyProfileTaskFinish(needToStop.getTask());
    }

    @Override
    public void prepare() {
    }

    @Override
    public void boot() {
    }

    @Override
    public void onComplete() {
        // add trace finish notification
        TracingContext.TracingThreadListenerManager.add(this);
    }

    @Override
    public void shutdown() {
        // remove trace listener
        TracingContext.TracingThreadListenerManager.remove(this);

        PROFILE_TASK_SCHEDULE.shutdown();

        PROFILE_EXECUTOR.shutdown();
    }

    public long getLastCommandCreateTime() {
        return lastCommandCreateTime;
    }

    /**
     * check profile task data success, make the re-check, prevent receiving wrong data from database or OAP
     * <pre>
     * (检查 分析任务 数据 是否成功，进行重新检查，防止从数据库或 OAP 接收错误数据)
     * </pre>
     */
    private CheckResult checkProfileTaskSuccess(ProfileTask task) {
        // 终端节点名称不能为空
        if (StringUtil.isEmpty(task.getFirstSpanOPName())) {
            return new CheckResult(false, "endpoint name cannot be empty");
        }

        // 监视器持续时间必须大于 1 分钟
        if (task.getDuration() < ProfileConstants.TASK_DURATION_MIN_MINUTE) {
            return new CheckResult(
                false, "monitor duration must greater than " + ProfileConstants.TASK_DURATION_MIN_MINUTE + " minutes");
        }
        // 监控任务的时长不能超过 15 分钟
        if (task.getDuration() > ProfileConstants.TASK_DURATION_MAX_MINUTE) {
            return new CheckResult(
                false,
                "The duration of the monitoring task cannot be greater than " + ProfileConstants.TASK_DURATION_MAX_MINUTE + " minutes"
            );
        }

        // min duration threshold
        // 最小持续时间阈值 必须 ≥ 0
        if (task.getMinDurationThreshold() < 0) {
            return new CheckResult(false, "min duration threshold must greater than or equals zero");
        }

        // dump period
        // 转储周期必须 ≥ 10 毫秒
        if (task.getThreadDumpPeriod() < ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS) {
            return new CheckResult(
                false,
                "dump period must be greater than or equals " + ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS + " milliseconds"
            );
        }

        // max sampling count
        // 最大采样计数必须 > 0
        if (task.getMaxSamplingCount() <= 0) {
            return new CheckResult(false, "max sampling count must greater than zero");
        }
        // 最大采样计数必须 < 10
        if (task.getMaxSamplingCount() >= ProfileConstants.TASK_MAX_SAMPLING_COUNT) {
            return new CheckResult(
                false, "max sampling count must less than " + ProfileConstants.TASK_MAX_SAMPLING_COUNT);
        }

        // check task queue, check only one task in a certain time
        // (检查任务队列，在一定时间内只检查一个任务)
        // 计算 分析任务 的完成时间
        long taskProcessFinishTime = calcProfileTaskFinishTime(task);
        for (ProfileTask profileTask : profileTaskList) {

            // if the end time of the task to be added is during the execution of any data, means is a error data
            // 要添加的任务 的 结束时间 必须 在 所有任务的开始时间 ~ 结束时间 范围 之外。
            if (taskProcessFinishTime >= profileTask.getStartTime() && taskProcessFinishTime <= calcProfileTaskFinishTime(
                profileTask)) {
                return new CheckResult(
                    false,
                    "there already have processing task in time range, could not add a new task again. processing task monitor endpoint name: "
                        + profileTask.getFirstSpanOPName()
                );
            }
        }

        return new CheckResult(true, null);
    }

    /**
     * 计算 分析任务 完成时间
     * @return 任务的开始时间 + 任务的持续时间
     */
    private long calcProfileTaskFinishTime(ProfileTask task) {
        // 任务的开始时间 + 任务的持续时间
        return task.getStartTime() + TimeUnit.MINUTES.toMillis(task.getDuration());
    }

    @Override
    public void afterMainThreadFinish(TracingContext tracingContext) {
        if (tracingContext.profileStatus().isBeingWatched()) {
            // stop profiling tracing context
            ProfileTaskExecutionContext currentExecutionContext = taskExecutionContext.get();
            if (currentExecutionContext != null) {
                currentExecutionContext.stopTracingProfile(tracingContext);
            }
        }
    }

    /**
     * check profile task is processable
     */
    private static class CheckResult {
        private boolean success;
        private String errorReason;

        public CheckResult(boolean success, String errorReason) {
            this.success = success;
            this.errorReason = errorReason;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorReason() {
            return errorReason;
        }
    }
}
