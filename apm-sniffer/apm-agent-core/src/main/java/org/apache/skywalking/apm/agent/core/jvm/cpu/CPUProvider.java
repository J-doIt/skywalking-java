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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.ProcessorUtil;
import org.apache.skywalking.apm.network.common.v3.CPU;

/**
 * <pre>
 * CPU 提供者，提供 #getCpuMetric() 方法，采集 CPU 指标。
 *
 * 为什么需要使用 ClassLoader#loadClass(className) 方法来加载 SunCpuAccessor 呢？
 *      因为 SkyWalking Agent 是通过 JavaAgent 机制，实际未引入，所以通过该方式加载类。
 * </pre>
 */
public enum CPUProvider {
    INSTANCE;
    /** CPU 指标访问器 */
    private CPUMetricsAccessor cpuMetricsAccessor;

    CPUProvider() {
        // 获得 CPU 数量
        int processorNum = ProcessorUtil.getNumberOfProcessors();
        // 获得 CPU 指标访问器（org.apache.skywalking.apm.agent.core.jvm.cpu.SunCpuAccessor）
        try {
            // 创建 SunCpuAccessor 对象
            // QFTODO：SunCpuAccessor 为什么不直接 new，而是要 通过 classLoad.loadClass 和 反射 的方式？
            //      SkyWalkingAgent.premain -> ... -> CPUProvider()
            //              到此，处在 JVM 的哪个阶段？（JavaAgent 机制）
            this.cpuMetricsAccessor = (CPUMetricsAccessor) CPUProvider.class.getClassLoader()
                                                                            .loadClass("org.apache.skywalking.apm.agent.core.jvm.cpu.SunCpuAccessor")
                                                                            .getConstructor(int.class)
                                                                            .newInstance(processorNum);
        } catch (Exception e) {
            // 发生异常，说明不支持，创建 NoSupportedCPUAccessor 对象。
            this.cpuMetricsAccessor = new NoSupportedCPUAccessor(processorNum);
            ILog logger = LogManager.getLogger(CPUProvider.class);
            logger.error(e, "Only support accessing CPU metrics in SUN JVM platform.");
        }
    }

    public CPU getCpuMetric() {
        // 获得 CPU 指标
        return cpuMetricsAccessor.getCPUMetrics();
    }
}
