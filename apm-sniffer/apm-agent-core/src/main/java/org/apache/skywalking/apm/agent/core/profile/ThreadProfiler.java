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

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.TracingContext;

/**
 * 线程分析
 */
public class ThreadProfiler {

    /** 当前的 TracingContext */
    private final TracingContext tracingContext;
    /** current tracing segment id */
    private final String traceSegmentId;
    /** 需要分析的线程 */
    private final Thread profilingThread;
    /** 分析任务执行的上下文 */
    private final ProfileTaskExecutionContext executionContext;

    /** 分析开始时间 */
    private long profilingStartTime;
    /** 最大监控段时间（分钟），如果当前段监控时间超出限制，则停止监控。 */
    private long profilingMaxTimeMills;

    /**
     * after min duration threshold check, it will start dump
     * <pre>
     * (检查最小持续时间阈值后，它将开始转储)
     * </pre>
     */
    private final ProfileStatusContext profilingStatus;
    /** 线程转储序列 */
    private int dumpSequence = 0;

    public ThreadProfiler(TracingContext tracingContext, String traceSegmentId, Thread profilingThread,
        ProfileTaskExecutionContext executionContext) {
        this.tracingContext = tracingContext;
        this.traceSegmentId = traceSegmentId;
        this.profilingThread = profilingThread;
        this.executionContext = executionContext;
        if (tracingContext.profileStatus() == null) {
            // 如果 tracingContext 的 分析状态 为空，则 新建一个 状态为 PENDING 的 ProfileStatusContext
            this.profilingStatus = ProfileStatusContext.createWithPending(tracingContext().createTime());
        } else {
            // 如果 tracingContext 的 分析状态 不为空，则 更新 当前ProfileStatusContext 的状态为 PENDING
            this.profilingStatus = tracingContext.profileStatus();
            this.profilingStatus.updateStatus(ProfileStatus.PENDING, tracingContext);
        }
        // 获取 最大监控段时间（分钟）
        this.profilingMaxTimeMills = TimeUnit.MINUTES.toMillis(Config.Profile.MAX_DURATION);
    }

    /**
     * If tracing start time greater than {@link ProfileTask#getMinDurationThreshold()}, then start to profiling trace
     * <pre>
     * (如果 跟踪开始时间 大于 ProfileTask.getMinDurationThreshold()，则开始分析跟踪)
     * </pre>
     */
    public void startProfilingIfNeed() {
        if (System.currentTimeMillis() - profilingStatus.firstSegmentCreateTime() > executionContext.getTask()
                                                                                       .getMinDurationThreshold()) {
            this.profilingStartTime = System.currentTimeMillis();
            this.profilingStatus.updateStatus(ProfileStatus.PROFILING, tracingContext);
        }
    }

    /**
     * Stop profiling status
     */
    public void stopProfiling() {
        this.profilingStatus.updateStatus(ProfileStatus.STOPPED, tracingContext);
    }

    /**
     * dump tracing thread and build thread snapshot
     * <pre>
     * (转储 跟踪线程 和 生成线程快照)
     * </pre>
     *
     * @return snapshot, if null means dump snapshot error, should stop it
     */
    public TracingThreadSnapshot buildSnapshot() {
        if (!isProfilingContinuable()) {
            return null;
        }

        long currentTime = System.currentTimeMillis();
        // dump thread
        StackTraceElement[] stackTrace;
        try {
            // QFTODO：获取此线程的堆栈转储的堆栈跟踪元素数组
            stackTrace = profilingThread.getStackTrace();

            // stack depth is zero, means thread is already run finished
            if (stackTrace.length == 0) {
                return null;
            }
        } catch (Exception e) {
            // dump error ignore and make this profiler stop
            return null;
        }

        // if is first dump, check is can start profiling
        if (dumpSequence == 0 && !executionContext.isStartProfileable()) {
            return null;
        }

        int dumpElementCount = Math.min(stackTrace.length, Config.Profile.DUMP_MAX_STACK_DEPTH);

        // use inverted order, because thread dump is start with bottom
        final ArrayList<String> stackList = new ArrayList<>(dumpElementCount);
        for (int i = dumpElementCount - 1; i >= 0; i--) {
            // 生成堆栈元素代码签名，并加入到 stackList
            stackList.add(buildStackElementCodeSignature(stackTrace[i]));
        }

        String taskId = executionContext.getTask().getTaskId();
        // 创建 TracingThreadSnapshot 并返回
        return new TracingThreadSnapshot(taskId, traceSegmentId, dumpSequence++, currentTime, stackList);
    }

    /**
     * build thread stack element code signature
     * <pre>
     * (生成线程堆栈元素代码签名)
     * </pre>
     *
     * @return code sign: className.methodName:lineNumber
     */
    private String buildStackElementCodeSignature(StackTraceElement element) {
        return element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    /**
     * matches profiling tracing context
     */
    public boolean matches(TracingContext context) {
        // match trace id
        return Objects.equal(context.getSegmentId(), tracingContext.getSegmentId());
    }

    /**
     * check whether profiling should continue
     *
     * @return if true means this thread profiling is continuable
     */
    private boolean isProfilingContinuable() {
        return System.currentTimeMillis() - profilingStartTime < profilingMaxTimeMills;
    }

    public TracingContext tracingContext() {
        return tracingContext;
    }

    public ProfileStatusContext profilingStatus() {
        return profilingStatus;
    }

}
