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

package org.apache.skywalking.apm.agent.core.jvm;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.jvm.clazz.ClassProvider;
import org.apache.skywalking.apm.agent.core.jvm.cpu.CPUProvider;
import org.apache.skywalking.apm.agent.core.jvm.gc.GCProvider;
import org.apache.skywalking.apm.agent.core.jvm.memory.MemoryProvider;
import org.apache.skywalking.apm.agent.core.jvm.memorypool.MemoryPoolProvider;
import org.apache.skywalking.apm.agent.core.jvm.thread.ThreadProvider;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetric;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * The <code>JVMService</code> represents a timer, which collectors JVM cpu, memory, memorypool, gc, thread and class info,
 * and send the collected info to Collector through the channel provided by {@link GRPCChannelManager}
 *
 * <pre>
 * JVM 指标服务，负责将 JVM 指标收集并发送给 Collector。
 * </pre>
 */
@DefaultImplementor
public class JVMService implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(JVMService.class);
    /** 收集指标定时任务 */
    private volatile ScheduledFuture<?> collectMetricFuture;
    /** 发送指标定时任务 */
    private volatile ScheduledFuture<?> sendMetricFuture;
    /** JVM指标发送器 */
    private JVMMetricsSender sender;
    private volatile double cpuUsagePercent;

    @Override
    public void prepare() throws Throwable {
        // 在 BootService 的 prepare 阶段，设置 sender
        sender = ServiceManager.INSTANCE.findService(JVMMetricsSender.class);
    }

    @Override
    public void boot() throws Throwable {
        // 创建 收集指标定时任务
        collectMetricFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("JVMService-produce"))
                                       .scheduleAtFixedRate(new RunnableWithExceptionProtection(
                                           this,
                                           new RunnableWithExceptionProtection.CallbackWhenException() {
                                               @Override
                                               public void handle(Throwable t) {
                                                   LOGGER.error("JVMService produces metrics failure.", t);
                                               }
                                           }
                                       ), 0, Config.Jvm.METRICS_COLLECT_PERIOD, TimeUnit.SECONDS);
        // 创建 发送指标定时任务
        sendMetricFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("JVMService-consume"))
                                    .scheduleAtFixedRate(new RunnableWithExceptionProtection(
                                        sender,
                                        new RunnableWithExceptionProtection.CallbackWhenException() {
                                            @Override
                                            public void handle(Throwable t) {
                                                LOGGER.error("JVMService consumes and upload failure.", t);
                                            }
                                        }
                                    ), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        collectMetricFuture.cancel(true);
        sendMetricFuture.cancel(true);
    }

    /**
     * 收集指标
     */
    @Override
    public void run() {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            // 创建 JVMMetric
            JVMMetric.Builder jvmBuilder = JVMMetric.newBuilder();
            jvmBuilder.setTime(currentTimeMillis);
            jvmBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric()); // 获得 CPU 指标
            jvmBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList()); // 获得 Memory 指标
            jvmBuilder.addAllMemoryPool(MemoryPoolProvider.INSTANCE.getMemoryPoolMetricsList()); // 获得 MemoryPool 指标
            jvmBuilder.addAllGc(GCProvider.INSTANCE.getGCList()); // 获得 GC 指标
            jvmBuilder.setThread(ThreadProvider.INSTANCE.getThreadMetrics()); // 获得 线程 指标
            jvmBuilder.setClazz(ClassProvider.INSTANCE.getClassMetrics()); //  获得 class 指标

            JVMMetric jvmMetric = jvmBuilder.build();
            // 提交 JVMMetric
            sender.offer(jvmMetric);

            // refresh cpu usage percent
            // （刷新 CPU 使用率百分比）
            cpuUsagePercent = jvmMetric.getCpu().getUsagePercent();
        } catch (Exception e) {
            LOGGER.error(e, "Collect JVM info fail.");
        }
    }

    public double getCpuUsagePercent() {
        return this.cpuUsagePercent;
    }

}
