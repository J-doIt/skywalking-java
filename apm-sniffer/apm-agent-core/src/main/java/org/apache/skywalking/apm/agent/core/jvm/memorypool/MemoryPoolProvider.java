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

package org.apache.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.MemoryPool;

/**
 * <pre>
 * MemoryPool 提供者，
 * 提供 #getMemoryPoolMetricList() 方法，采集 MemoryPool 指标数组。
 *
 * MemoryPool 和 Memory 的差别在于拆分的维度不同：
 *   Heap memory
 *      Code Cache
 *      Eden Space
 *      Survivor Space
 *      Tenured Gen
 *   non-heap memory
 *      Perm Gen
 *      native heap?(I guess)
 *
 * </pre>
 */
public enum MemoryPoolProvider {
    INSTANCE;

    /** MemoryPool 指标访问器接口 */
    private MemoryPoolMetricsAccessor metricAccessor;
    /** java.lang.management.MemoryPoolMXBean */
    private List<MemoryPoolMXBean> beans;

    MemoryPoolProvider() {
        // 创建 JVM GC 方式对应的 MemoryPoolMetricAccessor 对象

        // 获得 MemoryPoolMXBean 数组。每个 MemoryPoolMXBean 对象，代表一个区域类型
        beans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean bean : beans) {
            String name = bean.getName();
            // 找到对应的 GC 算法，创建对应的 MemoryPoolMetricAccessor 对象。
            MemoryPoolMetricsAccessor accessor = findByBeanName(name);
            if (accessor != null) {
                metricAccessor = accessor;
                break;
            }
        }
        // 未找到匹配的 GC 算法，创建 UnknownMemoryPool 对象
        if (metricAccessor == null) {
            metricAccessor = new UnknownMemoryPool();
        }
    }

    public List<MemoryPool> getMemoryPoolMetricsList() {
        return metricAccessor.getMemoryPoolMetricsList();
    }

    private MemoryPoolMetricsAccessor findByBeanName(String name) {
        if (name.indexOf("PS") > -1) {
            //Parallel (Old) collector ( -XX:+UseParallelOldGC )
            // 并行 GC
            return new ParallelCollectorModule(beans);
        } else if (name.indexOf("CMS") > -1) {
            // CMS collector ( -XX:+UseConcMarkSweepGC )
            // CMS GC
            return new CMSCollectorModule(beans);
        } else if (name.indexOf("G1") > -1) {
            // G1 collector ( -XX:+UseG1GC )
            // G1 GC
            return new G1CollectorModule(beans);
        } else if (name.equals("Survivor Space")) {
            // Serial collector ( -XX:+UseSerialGC )
            // 串行 GC
            return new SerialCollectorModule(beans);
        } else if (name.equals("ZHeap")) {
            // ZGC collector ( -XX:+UseZGC )
            // ZGC GC
            return new ZGCCollectorModule(beans);
        } else {
            // Unknown
            return null;
        }
    }
}
