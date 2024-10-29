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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.TracingContext;

/**
 * profile task execution context, it will create on process this profile task
 * <pre>
 * (分析任务执行的上下文中，它将在处理此分析任务时创建)
 * </pre>
 */
public class ProfileTaskExecutionContext {

    /** 当前 ProfileTask */
    private final ProfileTask task;

    // record current first endpoint profiling count, use this to check has available profile slot
    /** 记录 当前第一端点分析计数 ，使用此项 检查是否有可用的 profile slot */
    private final AtomicInteger currentEndpointProfilingCount = new AtomicInteger(0);

    /** profiling segment slot
     * 存放 trace线程分析，因为 为 端点 创建的分析任务，在一个时间段内会被不同线程执行。
     */
    private volatile AtomicReferenceArray<ThreadProfiler> profilingSegmentSlots;

    /** 当前 ProfileTask 的 执行结果 的 凭证 */
    private volatile Future profilingFuture;

    /** total started profiling tracing context count
     * （已启动的 分析跟踪上下文 的 总数） */
    private final AtomicInteger totalStartedProfilingCount = new AtomicInteger(0);

    public ProfileTaskExecutionContext(ProfileTask task) {
        this.task = task;
        profilingSegmentSlots = new AtomicReferenceArray<>(Config.Profile.MAX_PARALLEL * (Config.Profile.MAX_ACCEPT_SUB_PARALLEL + 1));
    }

    /**
     * start profiling this task
     * <pre>
     * 开始分析当前的任务
     * </pre>
     *
     * @param executorService 执行器
     */
    public void startProfiling(ExecutorService executorService) {
        // 创建 ProfileThread 并提交给 执行器 执行
        profilingFuture = executorService.submit(new ProfileThread(this));
    }

    /**
     * stop profiling
     */
    public void stopProfiling() {
        if (profilingFuture != null) {
            profilingFuture.cancel(true);
        }
    }

    /**
     * check have available slot to profile and add it
     * <pre>
     * (检查是否有可用的 slot 可供分析，如果有则添加到这个slot)
     * </pre>
     *
     * @return is add profile success
     */
    public ProfileStatusContext attemptProfiling(TracingContext tracingContext,
                                                 String traceSegmentId,
                                                 String firstSpanOPName) {
        // check has limited the max parallel profiling count
        // this.currentEndpointProfilingCount 必须小于 配置文件中的 Config.Profile.MAX_PARALLEL
        final int profilingEndpointCount = currentEndpointProfilingCount.get();
        if (profilingEndpointCount >= Config.Profile.MAX_PARALLEL) {
            return ProfileStatusContext.createWithNone();
        }

        // check first operation name matches
        // ProfileTask 的第一个执行名 必须等于 参数firstSpanOPName
        if (!Objects.equals(task.getFirstSpanOPName(), firstSpanOPName)) {
            return ProfileStatusContext.createWithNone();
        }

        // if out limit started profiling count then stop add profiling
        // totalStartedProfilingCount 必须 ≤ ProfileTask.maxSamplingCount
        if (totalStartedProfilingCount.get() > task.getMaxSamplingCount()) {
            return ProfileStatusContext.createWithNone();
        }

        // try to occupy slot
        // (尝试 占用 插槽)
        if (!currentEndpointProfilingCount.compareAndSet(profilingEndpointCount, profilingEndpointCount + 1)) {
            return ProfileStatusContext.createWithNone();
        }

        ThreadProfiler profiler;
        // 创建 ThreadProfiler 并尝试加入到 this.profilingSegmentSlots 数组中
        if ((profiler = addProfilingThread(tracingContext, traceSegmentId)) != null) {
            // 返回 ThreadProfiler 的 ProfileStatusContext
            return profiler.profilingStatus();
        }
        return ProfileStatusContext.createWithNone();
    }

    public boolean continueProfiling(TracingContext tracingContext, String traceSegmentId) {
        return addProfilingThread(tracingContext, traceSegmentId) != null;
    }

    private ThreadProfiler addProfilingThread(TracingContext tracingContext, String traceSegmentId) {
        // 创建 ThreadProfiler
        final ThreadProfiler threadProfiler = new ThreadProfiler(
            tracingContext, traceSegmentId, Thread.currentThread(), this);
        int slotLength = profilingSegmentSlots.length();
        // 将 新的ThreadProfiler 加入到 profilingSegmentSlots 数组中（从前往后，知道加入成功）
        for (int slot = 0; slot < slotLength; slot++) {
            if (profilingSegmentSlots.compareAndSet(slot, null, threadProfiler)) {
                return threadProfiler;
            }
        }
        // add profiling thread failure, so ignore it
        return null;
    }

    /**
     * profiling recheck
     */
    public void profilingRecheck(TracingContext tracingContext, String traceSegmentId, String firstSpanOPName) {
        // if started, keep profiling
        if (tracingContext.profileStatus().isBeingWatched()) {
            return;
        }

        // update profiling status
        tracingContext.profileStatus()
            .updateStatus(attemptProfiling(tracingContext, traceSegmentId, firstSpanOPName));
    }

    /**
     * find tracing context and clear on slot
     */
    public void stopTracingProfile(TracingContext tracingContext) {
        // find current tracingContext and clear it
        int slotLength = profilingSegmentSlots.length();
        for (int slot = 0; slot < slotLength; slot++) {
            ThreadProfiler currentProfiler = profilingSegmentSlots.get(slot);
            if (currentProfiler != null && currentProfiler.matches(tracingContext)) {
                profilingSegmentSlots.set(slot, null);

                // setting stop running
                currentProfiler.stopProfiling();
                if (currentProfiler.profilingStatus().isFromFirstSegment()) {
                    currentEndpointProfilingCount.addAndGet(-1);
                }
                break;
            }
        }
    }

    public ProfileTask getTask() {
        return task;
    }

    public AtomicReferenceArray<ThreadProfiler> threadProfilerSlots() {
        return profilingSegmentSlots;
    }

    public boolean isStartProfileable() {
        // check is out of max sampling count check
        return totalStartedProfilingCount.incrementAndGet() <= task.getMaxSamplingCount();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ProfileTaskExecutionContext that = (ProfileTaskExecutionContext) o;
        return Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task);
    }
}
