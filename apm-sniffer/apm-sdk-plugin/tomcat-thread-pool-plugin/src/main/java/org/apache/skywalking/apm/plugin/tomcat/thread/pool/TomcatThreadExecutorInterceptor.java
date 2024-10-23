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

package org.apache.skywalking.apm.plugin.tomcat.thread.pool;

import org.apache.skywalking.apm.agent.core.meter.MeterFactory;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

public class TomcatThreadExecutorInterceptor implements InstanceConstructorInterceptor {

    private static final String METER_NAME = "thread_pool";
    private static final String METRIC_POOL_NAME_TAG_NAME = "pool_name";
    private static final String THREAD_POOL_NAME = "tomcat_execute_pool";
    private static final String METRIC_TYPE_TAG_NAME = "metric_type";

    /**
     * @param objInst 被增强了的 tomcat 的 ThreadPoolExecutor
     * @param allArguments
     * @throws Throwable
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) throws Throwable {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) objInst;
        // 创建一个 Gauge 指标 来监控 核心线程池大小
        MeterFactory.gauge(METER_NAME, () -> (double) threadPoolExecutor.getCorePoolSize())
                    .tag(METRIC_POOL_NAME_TAG_NAME, THREAD_POOL_NAME)
                    .tag(METRIC_TYPE_TAG_NAME, "core_pool_size")
                    .build();
        // 创建一个 Gauge 指标 来监控 最大线程池大小
        MeterFactory.gauge(METER_NAME, () -> (double) threadPoolExecutor.getMaximumPoolSize())
                    .tag(METRIC_POOL_NAME_TAG_NAME, THREAD_POOL_NAME)
                    .tag(METRIC_TYPE_TAG_NAME, "max_pool_size")
                    .build();
        // 创建一个 Gauge 指标 来监控 当前线程池大小
        MeterFactory.gauge(METER_NAME, () -> (double) threadPoolExecutor.getPoolSize())
                    .tag(METRIC_POOL_NAME_TAG_NAME, THREAD_POOL_NAME)
                    .tag(METRIC_TYPE_TAG_NAME, "pool_size")
                    .build();
        // 创建一个 Gauge 指标 来监控 任务队列大小
        MeterFactory.gauge(METER_NAME, () -> (double) threadPoolExecutor.getQueue().size())
                    .tag(METRIC_POOL_NAME_TAG_NAME, THREAD_POOL_NAME)
                    .tag(METRIC_TYPE_TAG_NAME, "queue_size")
                    .build();
        // 创建一个 Gauge 指标 来监控 活跃线程数
        MeterFactory.gauge(METER_NAME, () -> (double) threadPoolExecutor.getActiveCount())
                    .tag(METRIC_POOL_NAME_TAG_NAME, THREAD_POOL_NAME)
                    .tag(METRIC_TYPE_TAG_NAME, "active_size")
                    .build();
    }
}
