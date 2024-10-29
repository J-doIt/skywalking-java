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

package org.apache.skywalking.apm.agent.core.sampling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.dynamic.ConfigurationDiscoveryService;
import org.apache.skywalking.apm.agent.core.conf.dynamic.watcher.SamplingRateWatcher;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * The <code>SamplingService</code> take charge of how to sample the {@link TraceSegment}. Every {@link TraceSegment}s
 * have been traced, but, considering CPU cost of serialization/deserialization, and network bandwidth, the agent do NOT
 * send all of them to collector, if SAMPLING is on.
 * <p>
 * By default, SAMPLING is on, and  {@link Config.Agent#SAMPLE_N_PER_3_SECS }
 *
 * <pre>
 * (SamplingService 负责如何采样 TraceSegment。每个 TraceSegment 都被跟踪了，
 * 但是，考虑到 序列化/反序列化 的CPU成本和网络带宽，如果 SAMPLING 处于开启状态，代理不会将它们全部发送给收集器。
 * 默认情况下，SAMPLING 是开启的，Config.Agent#SAMPLE_N_PER_3_SECS)
 * </pre>
 */
@DefaultImplementor
public class SamplingService implements BootService {
    private static final ILog LOGGER = LogManager.getLogger(SamplingService.class);

    /** 采样机制 的 开关 */
    private volatile boolean on = false;
    /** 采样因子（采集次数） */
    private volatile AtomicInteger samplingFactorHolder;
    /** 重置采样因子 的 结果凭据 */
    private volatile ScheduledFuture<?> scheduledFuture;

    /** 动态配置观察器 */
    private SamplingRateWatcher samplingRateWatcher;
    /** 单线程定时执行器（SkywalkingAgent-n-SamplingService-），负责 重置采样因子 */
    private ScheduledExecutorService service;

    @Override
    public void prepare() {
    }

    @Override
    public void boot() {
        // 初始化 单线程定时执行器
        service = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("SamplingService"));
        samplingRateWatcher = new SamplingRateWatcher("agent.sample_n_per_3_secs", this);
        // 注册动态配置观察器
        ServiceManager.INSTANCE.findService(ConfigurationDiscoveryService.class)
                               .registerAgentConfigChangeWatcher(samplingRateWatcher);

        // 处理 更改的 采样率
        handleSamplingRateChanged();
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {
        // 如果 scheduledFuture 不为空，取消 scheduledFuture
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    /**
     * When the sampling mechanism is on and the sample limited is not reached, the trace segment
     * should be traced. If the sampling mechanism is off, it means that all trace segments should
     * be traced.
     * <pre>
     * (当 采样机制 开启 且 未达到采样限制 时，应 跟踪 trace segment。
     * 如果 采样机制 关闭，则意味着应该 跟踪 所有 trace segment。)
     * </pre>
     *
     * @param operationName 新 tracing context 的第一个操作名
     * @return true if should sample this trace segment. When sampling mechanism is on, return true if sample limited is not reached.
     */
    public boolean trySampling(String operationName) {
        // 如果 采样机制 开启
        if (on) {
            int factor = samplingFactorHolder.get();
            // 采样因子 < 配置文件中的 采样率
            if (factor < samplingRateWatcher.getSamplingRate()) {
                // 采样因子++ 成功返回 true
                return samplingFactorHolder.compareAndSet(factor, factor + 1);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Increase the sampling factor by force, to avoid sampling too many traces. If many distributed traces require
     * sampled, the trace beginning at local, has less chance to be sampled.
     * <pre>
     * (通过强制增加采样因子，以避免 采样 过多的 traces 。
     * 如果需要对 许多 分布式 traces 进行采样，则从本地开始的 traces 被采样的机会较小。)
     * </pre>
     */
    public void forceSampled() {
        // 如果 采样机制 开启
        if (on) {
            // 采样因子++
            samplingFactorHolder.incrementAndGet();
        }
    }

    /** 重置采样因子 */
    private void resetSamplingFactor() {
        samplingFactorHolder = new AtomicInteger(0);
    }

    /**
     * Handle the samplingRate changed.
     * 处理 更改的 采样率。
     */
    public void handleSamplingRateChanged() {
        // 如果 采样率 > 0
        if (samplingRateWatcher.getSamplingRate() > 0) {
            // 没开
            if (!on) {
                // 打开
                on = true;
                // 重置采样因子
                this.resetSamplingFactor();
                // 每 3s 执行一次 RunnableWithExceptionProtection 任务（执行 重置采样因子，失败则执行 callback）
                scheduledFuture = service.scheduleAtFixedRate(new RunnableWithExceptionProtection(
                    this::resetSamplingFactor/* 重置采样因子 */, t -> LOGGER.error("unexpected exception.", t)/* callback */), 0, 3, TimeUnit.SECONDS);
                LOGGER.debug(
                    "Agent sampling mechanism started. Sample {} traces in 3 seconds.",
                    samplingRateWatcher.getSamplingRate()
                );
            }
        } else {
            // 如果 采样率 ≤ 0，且 开关是 开的
            if (on) {
                // 取消 scheduledFuture
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(true);
                }
                // 关闭
                on = false;
            }
        }
    }
}
