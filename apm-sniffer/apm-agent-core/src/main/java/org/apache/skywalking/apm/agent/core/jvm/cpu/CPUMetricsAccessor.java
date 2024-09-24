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

package org.apache.skywalking.apm.agent.core.jvm.cpu;

import org.apache.skywalking.apm.network.common.v3.CPU;

/**
 * The unit of CPU usage is 1/10000. The backend is using `avg` func directly, and query for percentage requires this
 * unit.
 *
 * <pre>
 * (CPU占用率单位为 1/10000 。后台直接使用' avg '函数，查询 percentage 需要这个单位。)
 *
 * CPU 指标访问器抽象类。
 * </pre>
 */
public abstract class CPUMetricsAccessor {
    /** 获得进程占用 CPU 时长，单位：纳秒 */
    private long lastCPUTimeNs;
    /** 最后取样时间，单位：纳秒 */
    private long lastSampleTimeNs;
    /** CPU 数量 */
    private final int cpuCoreNum;

    public CPUMetricsAccessor(int cpuCoreNum) {
        this.cpuCoreNum = cpuCoreNum;
    }

    /**
     * 初始化 lastCPUTimeNs 、lastSampleTimeNs
     */
    protected void init() {
        lastCPUTimeNs = this.getCpuTime();
        lastSampleTimeNs = System.nanoTime();
    }

    /**
     * 获得 CPU 时间
     */
    protected abstract long getCpuTime();

    /**
     * <pre>
     * 获得 CPU 指标
     *
     * now - lastSampleTimeNs ，获得 JVM 进程启动总时长。
     *      这里为什么相减呢？因为 CPUMetricAccessor 不是在 JVM 启动时就进行计算，通过相减，解决偏差。
     * </pre>
     */
    public CPU getCPUMetrics() {
        long cpuTime = this.getCpuTime();
        // CPU 占用时间
        long cpuCost = cpuTime - lastCPUTimeNs;
        long now = System.nanoTime();

        try {
            CPU.Builder cpuBuilder = CPU.newBuilder();
            // CPU 占用率 = CPU 占用时间 / ( 过去时间 * CPU 数量 ) * 100
            return cpuBuilder.setUsagePercent(cpuCost * 1.0d / ((now - lastSampleTimeNs) * cpuCoreNum) * 100).build();
        } finally {
            lastCPUTimeNs = cpuTime;
            lastSampleTimeNs = now;
        }
    }
}
