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

package org.apache.skywalking.apm.plugin.dubbo;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.transport.AbstractServer;
import org.apache.skywalking.apm.agent.core.meter.MeterFactory;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 以收集和报告与线程池相关指标，增强监控能力。
 *
 * <pre>
 * 增强类：com.alibaba.dubbo.remoting.transport.AbstractServer
 * 增强构造方法：AbstractServer(URL url, ChannelHandler handler)
 * </pre>
 */
public class AbstractServerConstructorInterceptor implements InstanceConstructorInterceptor {
    private static final String METER_NAME = "thread_pool";
    private static final String METRIC_POOL_NAME_TAG_NAME = "pool_name";
    private static final String METRIC_TYPE_TAG_NAME = "metric_type";

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) throws Throwable {
        // 获取 ExecutorService 引用
        Field executorField = AbstractServer.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(objInst);

        // 从构造参数中提取端口号
        URL url = (URL) allArguments[0];
        int port = url.getPort();

        // 检查 ExecutorService 是否为 ThreadPoolExecutor 以收集指标
        if (!(executor instanceof ThreadPoolExecutor)) {
            return;
        }
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
        String threadPoolName = String.format("DubboServerHandler-%s", port);

        // 报告线程池核心、最大、历史最大、当前大小、队列大小、活跃的大小、任务数、以及 完成的任务数 的度量指标
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getCorePoolSize()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "core_pool_size")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getMaximumPoolSize()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "max_pool_size")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getLargestPoolSize()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "largest_pool_size")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getPoolSize()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "pool_size")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getQueue().size()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "queue_size")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getActiveCount()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "active_size")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getTaskCount()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "task_count")
                .build();
        MeterFactory.gauge(METER_NAME, () -> (double) (threadPoolExecutor.getCompletedTaskCount()))
                .tag(METRIC_POOL_NAME_TAG_NAME, threadPoolName)
                .tag(METRIC_TYPE_TAG_NAME, "completed_task_count")
                .build();
    }
}
