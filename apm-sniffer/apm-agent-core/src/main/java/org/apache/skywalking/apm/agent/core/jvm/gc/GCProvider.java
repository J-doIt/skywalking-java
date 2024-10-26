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

package org.apache.skywalking.apm.agent.core.jvm.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.GC;

/**
 * <pre>
 * GC 提供者，
 * 提供 #getGCList() 方法，采集 GC 指标数组。
 *
 * language-agent/JVMMetric.proto 的 message GC
 * </pre>
 */
public enum GCProvider {
    INSTANCE;

    /** GC 指标访问器接口 */
    private GCMetricAccessor metricAccessor;
    /** java.lang.management.GarbageCollectorMXBean */
    private List<GarbageCollectorMXBean> beans;

    GCProvider() {
        // 获得 GarbageCollectorMXBean 数组。
        beans = ManagementFactory.getGarbageCollectorMXBeans();
        // 找到 GC 算法，创建对应的 GCMetricAccessor 对象。
        for (GarbageCollectorMXBean bean : beans) {
            String name = bean.getName();
            // 找到对应的 GC 算法，创建对应的 GCMetricAccessor 对象。
            GCMetricAccessor accessor = findByBeanName(name);
            if (accessor != null) {
                metricAccessor = accessor;
                break;
            }
        }

        // 找不到 GC 算法，创建 UnknowGC 对象。
        if (metricAccessor == null) {
            this.metricAccessor = new UnknowGC();
        }
    }

    /**
     * 获得 GC 指标数组
     */
    public List<GC> getGCList() {
        return metricAccessor.getGCList();
    }

    private GCMetricAccessor findByBeanName(String name) {
        if (name.indexOf("PS") > -1) {
            //Parallel (Old) collector ( -XX:+UseParallelOldGC )
            return new ParallelGCModule(beans);
        } else if (name.indexOf("ConcurrentMarkSweep") > -1) {
            // CMS collector ( -XX:+UseConcMarkSweepGC )
            return new CMSGCModule(beans);
        } else if (name.indexOf("G1") > -1) {
            // G1 collector ( -XX:+UseG1GC )
            return new G1GCModule(beans);
        } else if (name.equals("MarkSweepCompact")) {
            // Serial collector ( -XX:+UseSerialGC )
            return new SerialGCModule(beans);
        } else if (name.indexOf("ZGC") > -1) {
            // Z garbage collector ( -XX:+UseZGC )
            return new ZGCModule(beans);
        } else {
            // Unknown
            return null;
        }
    }
}
