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

package org.apache.skywalking.apm.agent.core.remote;

import io.grpc.Channel;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.jvm.LoadedLibraryCollector;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.OSUtil;
import org.apache.skywalking.apm.agent.core.util.InstanceJsonPropertiesUtil;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.management.v3.InstancePingPkg;
import org.apache.skywalking.apm.network.management.v3.InstanceProperties;
import org.apache.skywalking.apm.network.management.v3.ManagementServiceGrpc;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * 服务管理客户端（报告 服务实例属性 到 OAP）
 */
@DefaultImplementor
public class ServiceManagementClient implements BootService, Runnable, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(ServiceManagementClient.class);
    /** 服务实例属性 列表 */
    private static List<KeyStringValuePair> SERVICE_INSTANCE_PROPERTIES;

    /** grpc channel 状态 */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    /** 管理服务 的 grpc stub */
    private volatile ManagementServiceGrpc.ManagementServiceBlockingStub managementServiceBlockingStub;
    /** this.run() 执行结果 的 凭证 */
    private volatile ScheduledFuture<?> heartbeatFuture;
    /** 使用原子整数来控制属性报告的频率，要么发送属性报告，要么发送心跳检测 */
    private volatile AtomicInteger sendPropertiesCounter = new AtomicInteger(0);

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            // 当连接成功时，使用 通道 重新 初始化 ManagementService 的 阻塞式 grpc stub
            managementServiceBlockingStub = ManagementServiceGrpc.newBlockingStub(channel);
        } else {
            // 当连接关闭时，设置 ManagementService 为 null
            managementServiceBlockingStub = null;
        }
        this.status = status;
    }

    @Override
    public void prepare() {
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中，方便监听 grpc channel 状态变化
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);

        // 解析json格式的服务实例属性
        SERVICE_INSTANCE_PROPERTIES = InstanceJsonPropertiesUtil.parseProperties();
    }

    @Override
    public void boot() {
        heartbeatFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("ServiceManagementClient")
        ).scheduleAtFixedRate( // 每 HEARTBEAT_PERIOD 秒执行一次 this.run()
            new RunnableWithExceptionProtection(
                this, // 任务
                t -> LOGGER.error("unexpected exception.", t) // 错误回调
            ), 0, Config.Collector.HEARTBEAT_PERIOD,
            TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        // 取消 heartbeatFuture
        heartbeatFuture.cancel(true);
    }

    @Override
    public void run() {
        LOGGER.debug("ServiceManagementClient running, status:{}.", status);

        // 连接中，才发送 服务实例属性 到 OAP 后端
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            try {
                // 确保 管理服务GRPC阻塞存根 不为空
                if (managementServiceBlockingStub != null) {
                    // 使用原子整数 sendPropertiesCounter 来控制属性报告的频率
                    if (Math.abs(
                        sendPropertiesCounter.getAndAdd(1)) % Config.Collector.PROPERTIES_REPORT_PERIOD_FACTOR == 0) {
                        // 如果计数器满足条件，则报告实例属性
                        managementServiceBlockingStub
                            .withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS) // 设置请求的超时时间
                            // 构建，并 执行 stub 的 reportInstanceProperties() 发送 实例属性
                            .reportInstanceProperties(InstanceProperties.newBuilder() // 构建实例属性
                                                                        .setService(Config.Agent.SERVICE_NAME) // 设置服务名称
                                                                        .setServiceInstance(Config.Agent.INSTANCE_NAME) // 设置服务实例名称
                                                                        .addAllProperties(OSUtil.buildOSInfo(Config.OsInfo.IPV4_LIST_SIZE)) // 添加操作系统信息
                                                                        .addAllProperties(SERVICE_INSTANCE_PROPERTIES) // 添加服务实例属性
                                                                        .addAllProperties(LoadedLibraryCollector.buildJVMInfo()) // 添加 JVM 信息
                                                                        .build());
                    } else {
                        // 如果计数器不满足条件，则发送心跳包
                        final Commands commands = managementServiceBlockingStub.withDeadlineAfter(
                            GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
                        ).
                                // 构建，并 执行 stub 的 keepAlive() 发送 心跳
                                keepAlive(InstancePingPkg.newBuilder()
                                                   .setService(Config.Agent.SERVICE_NAME)
                                                   .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                                   .build());

                        // 将 Commands 交给 CommandService 去执行
                        ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                    }
                }
            } catch (Throwable t) {
                LOGGER.error(t, "ServiceManagementClient execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }
}
