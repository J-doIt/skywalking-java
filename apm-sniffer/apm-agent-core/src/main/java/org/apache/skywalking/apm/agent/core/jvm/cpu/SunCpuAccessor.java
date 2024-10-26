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

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

/**
 * 基于 SUN 提供的方法，获取 CPU 指标访问器
 */
public class SunCpuAccessor extends CPUMetricsAccessor {
    /** com.sun.management.OperatingSystemMXBean */
    private final OperatingSystemMXBean osMBean;

    public SunCpuAccessor(int cpuCoreNum) {
        // 设置 CPU 数量
        super(cpuCoreNum);
        // 获得 OperatingSystemMXBean 对象
        this.osMBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.init();
    }

    @Override
    protected long getCpuTime() {
        // 获得 JVM 进程占用 CPU 总时长
        return osMBean.getProcessCpuTime();
    }
}
