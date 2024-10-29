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

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Profile task process thread, dump the executing thread stack.
 * <pre>
 * (分析任务程序线程，转储正在执行的线程堆栈。)
 * </pre>
 */
public class ProfileThread implements Runnable {

    private static final ILog LOGGER = LogManager.getLogger(ProfileThread.class);

    /** 分析任务的执行上下文 */
    private final ProfileTaskExecutionContext taskExecutionContext;

    /** BootService，ProfileTask执行器服务 */
    private final ProfileTaskExecutionService profileTaskExecutionService;
    /** BootService，ProfileTask通道服务 */
    private final ProfileTaskChannelService profileTaskChannelService;

    public ProfileThread(ProfileTaskExecutionContext taskExecutionContext) {
        this.taskExecutionContext = taskExecutionContext;
        profileTaskExecutionService = ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class);
        profileTaskChannelService = ServiceManager.INSTANCE.findService(ProfileTaskChannelService.class);
    }

    @Override
    public void run() {

        try {
            // 分析
            profiling(taskExecutionContext);
        } catch (InterruptedException e) {
            // ignore interrupted
            // means current task has stopped
            // 忽略中断信号
        } catch (Exception e) {
            LOGGER.error(e, "Profiling task fail. taskId:{}", taskExecutionContext.getTask().getTaskId());
        } finally {
            // finally stop current profiling task, tell execution service task has stop
            // 停止当前的分析任务，告诉 执行服务 任务已停止
            profileTaskExecutionService.stopCurrentProfileTask(taskExecutionContext);
        }

    }

    /**
     * start profiling
     */
    private void profiling(ProfileTaskExecutionContext executionContext) throws InterruptedException {

        // 线程转储周期 (ms)
        int maxSleepPeriod = executionContext.getTask().getThreadDumpPeriod();

        // run loop when current thread still running
        // （当前线程仍在运行时运行循环）
        long currentLoopStartTime = -1;
        // 确保 当前线程 未被中断
        while (!Thread.currentThread().isInterrupted()) {
            currentLoopStartTime = System.currentTimeMillis();

            // each all slot
            AtomicReferenceArray<ThreadProfiler> profilers = executionContext.threadProfilerSlots();
            int profilerCount = profilers.length();
            for (int slot = 0; slot < profilerCount; slot++) {
                ThreadProfiler currentProfiler = profilers.get(slot);
                if (currentProfiler == null) {
                    continue;
                }

                switch (currentProfiler.profilingStatus().get()) {

                    case PENDING:
                        // check tracing context running time
                        currentProfiler.startProfilingIfNeed();
                        break;

                    case PROFILING:
                        // 构建 dump stack
                        TracingThreadSnapshot snapshot = currentProfiler.buildSnapshot();
                        if (snapshot != null) {
                            // 将 TracingThreadSnapshot 加入到 ProfileTaskChannelService.snapshotQueue 中，方便该服务发送快照到 OAP。
                            profileTaskChannelService.addProfilingSnapshot(snapshot);
                        } else {
                            // tell execution context current tracing thread dump failed, stop it
                            // (告诉 执行上下文 当前跟踪线程 转储失败，停止它)
                            executionContext.stopTracingProfile(currentProfiler.tracingContext());
                        }
                        break;

                }
            }

            // sleep to next period
            // if out of period, sleep one period
            long needToSleep = (currentLoopStartTime + maxSleepPeriod) - System.currentTimeMillis();
            needToSleep = needToSleep > 0 ? needToSleep : maxSleepPeriod;
            Thread.sleep(needToSleep);
        }
    }

}
