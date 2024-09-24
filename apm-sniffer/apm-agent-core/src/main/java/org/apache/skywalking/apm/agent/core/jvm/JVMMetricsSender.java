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

import io.grpc.Channel;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetric;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetricReportServiceGrpc;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

@DefaultImplementor
public class JVMMetricsSender implements BootService, Runnable, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(JVMMetricsSender.class);

    /** 连接状态 */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    /** Stub */
    private volatile JVMMetricReportServiceGrpc.JVMMetricReportServiceBlockingStub stub = null;

    /** 收集指标队列 */
    private LinkedBlockingQueue<JVMMetric> queue;

    @Override
    public void prepare() {
        // 创建阻塞队列
        queue = new LinkedBlockingQueue<>(Config.Jvm.BUFFER_SIZE);
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {

    }

    public void offer(JVMMetric metric) {
        // drop last message and re-deliver
        if (!queue.offer(metric)) {
            queue.poll();
            queue.offer(metric);
        }
    }

    /**
     * 发送指标
     */
    @Override
    public void run() {
        // 连接中，才发送 JVM 指标
        if (status == GRPCChannelStatus.CONNECTED) {
            try {
                JVMMetricCollection.Builder builder = JVMMetricCollection.newBuilder();
                LinkedList<JVMMetric> buffer = new LinkedList<>();
                // 从队列移除所有 JVMMetric 到 buffer 数组
                queue.drainTo(buffer);
                // 使用 Stub ，批量发送到 Collector
                if (buffer.size() > 0) {
                    builder.addAllMetrics(buffer);
                    builder.setService(Config.Agent.SERVICE_NAME);
                    builder.setServiceInstance(Config.Agent.INSTANCE_NAME);
                    //
                    Commands commands = stub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS)
                                            .collect(builder.build());
                    //
                    ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "send JVM metrics to Collector fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        // 当连接成功时，创建阻塞 Stub
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            stub = JVMMetricReportServiceGrpc.newBlockingStub(channel);
        }
        this.status = status;
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }
}
