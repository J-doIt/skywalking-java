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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.IS_RESOLVE_DNS_PERIODICALLY;

/**
 * <pre>
 * gRPC Channel 管理器。
 * 实现 BootService 、Runnable 接口。
 * GRPCChannelManager 负责管理与 Collector Agent gRPC Server 集群的连接的管理，提供给其他服务使用。
 * </pre>
 */
@DefaultImplementor
public class GRPCChannelManager implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(GRPCChannelManager.class);

    /**
     * 连接 gRPC Server 的 Channel 。
     * 同一时间，GRPCChannelManager 只连接一个 Collector Agent gRPC Server 节点，并且在 Channel 不因为各种网络问题断开的情况下，持续保持。
     */
    private volatile GRPCChannel managedChannel = null;
    /** 定时重连 gRPC Server 的定时任务（this.run() 重连 GRPCChannel） 的结果凭证 */
    private volatile ScheduledFuture<?> connectCheckFuture;
    /**
     * 是否重连。
     * 当 Channel 未连接需要连接，或者 Channel 断开需要重连时，标记 reconnect = true 。后台线程会根据该标识进行连接( 重连 )。
     */
    private volatile boolean reconnect = true;
    /** 随机获得 this.grpcServers 中的 OAP地址 */
    private final Random random = new Random();
    /**
     * 监听器数组。
     * 使用 Channel 的其他服务，注册监听器到 GRPCChannelManager 上，
     * 从而根据连接状态( org.skywalking.apm.agent.core.remote.GRPCChannelStatus )，实现自定义逻辑。
     */
    private final List<GRPCChannelListener> listeners = Collections.synchronizedList(new LinkedList<>());
    /** OAP（后端） 地址 列表*/
    private volatile List<String> grpcServers;
    /** 当前 已选择的 OAP 地址 在 grpcServers 中的 index */
    private volatile int selectedIdx = -1;
    /** 重连计数（GRPCChannel重新设置时，reconnectCount 也重置为 0 了） */
    private volatile int reconnectCount = 0;

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
        // 检查 配置文件 中的 OAP（后端） 地址
        if (Config.Collector.BACKEND_SERVICE.trim().length() == 0) {
            LOGGER.error("Collector server addresses are not set.");
            LOGGER.error("Agent will not uplink any data.");
            return;
        }
        // 设置 OAP地址 列表
        grpcServers = Arrays.asList(Config.Collector.BACKEND_SERVICE.split(","));

        // 创建定时任务。
        // 该定时任务无初始化延迟，每 Config.GRPC_CHANNEL_CHECK_INTERVAL ( 默认：30 s ) 执行一次 #run() 方法。
        connectCheckFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("GRPCChannelManager")
        ).scheduleAtFixedRate(
            new RunnableWithExceptionProtection(
                this,
                t -> LOGGER.error("unexpected exception.", t)
            ), 0, Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL, TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {
        // connectCheckFuture 不为空，则 取消 connectCheckFuture
        if (connectCheckFuture != null) {
            connectCheckFuture.cancel(true);
        }
        // managedChannel 不为空，则 关闭 managedChannel
        if (managedChannel != null) {
            managedChannel.shutdownNow();
        }
        LOGGER.debug("Selected collector grpc service shutdown.");
    }

    @Override
    public void run() {
        LOGGER.debug("Selected collector grpc service running, reconnect:{}.", reconnect);
        // 已启用定期解析 DNS 以更新接收方服务地址 && reconnect
        if (IS_RESOLVE_DNS_PERIODICALLY && reconnect) {
            // 获取 Collector skywalking 追踪接收器服务地址。（）
            grpcServers = Arrays.stream(Config.Collector.BACKEND_SERVICE.split(","))
                    .filter(StringUtil::isNotBlank) // 过滤掉 空值
                    .map(eachBackendService -> eachBackendService.split(":")) // 将每个服务地址拆分为域名和端口
                    .filter(domainPortPairs -> {
                        // 检查 OAP 地址 格式 是否正确
                        if (domainPortPairs.length < 2) {
                            LOGGER.debug("Service address [{}] format error. The expected format is IP:port", domainPortPairs[0]);
                            return false;
                        }
                        return true;
                    })
                    .flatMap(domainPortPairs -> {
                        try {
                            // 解析域名为 IP 地址
                            return Arrays.stream(InetAddress.getAllByName(domainPortPairs[0]))
                                    .map(InetAddress::getHostAddress) // 获取主机地址
                                    .map(ip -> String.format("%s:%s", ip, domainPortPairs[1])); // 重新组合 IP 和端口
                        } catch (Throwable t) {
                            LOGGER.error(t, "Failed to resolve {} of backend service.", domainPortPairs[0]);
                        }
                        // 如果解析失败，返回一个空的 Stream
                        return Stream.empty();
                    })
                    .distinct() // 去重
                    .collect(Collectors.toList()); // 收集结果到列表
        }

        // 当 reconnect = true 时，才执行连接( 重连 )
        if (reconnect) {
            // 当本地已经获取到 Collector Agent gRPC Server 集群地址时
            if (grpcServers.size() > 0) {
                String server = "";
                try {
                    // 随机获得准备链接的 Collector Agent gRPC Server
                    int index = Math.abs(random.nextInt()) % grpcServers.size();
                    // 随机选中的 index 非 selectedIdx
                    if (index != selectedIdx) {
                        // 更新 selectedIdx
                        selectedIdx = index;

                        // 当前的 OAP 地址
                        server = grpcServers.get(index);
                        String[] ipAndPort = server.split(":");

                        // 关闭 当前的 GRPCChannel
                        if (managedChannel != null) {
                            managedChannel.shutdownNow();
                        }

                        // 重新创建一个  GRPCChannel ，并连接
                        managedChannel = GRPCChannel.newBuilder(ipAndPort[0], Integer.parseInt(ipAndPort[1]))
                                                    .addManagedChannelBuilder(new StandardChannelBuilder())
                                                    .addManagedChannelBuilder(new TLSChannelBuilder())
                                                    .addChannelDecorator(new AgentIDDecorator())
                                                    .addChannelDecorator(new AuthenticationDecorator())
                                                    .build();
                        // 重连计数 置为 0
                        reconnectCount = 0;
                        // 不需要 重连
                        reconnect = false;
                        // 通知 GRPCChannelListener，GRPCChannel 的状态 变为 已连接
                        notify(GRPCChannelStatus.CONNECTED);
                    } else if (managedChannel.isConnected(++reconnectCount > Config.Agent.FORCE_RECONNECTION_PERIOD)) {
                        // Reconnect to the same server is automatically done by GRPC,
                        // therefore we are responsible to check the connectivity and
                        // set the state and notify listeners
                        // (重新连接到同一服务器由 GRPC 自动完成，因此，我们负责 检查连接，并设置 state，并 通知 listener)
                        // 重连计数 置为 0
                        reconnectCount = 0;
                        // 重置 reconnect 为 false
                        reconnect = false;
                        // 通知 GRPCChannelListener，GRPCChannel 的状态 变为 已连接
                        notify(GRPCChannelStatus.CONNECTED);
                    }

                    return;
                } catch (Throwable t) {
                    LOGGER.error(t, "Create channel to {} fail.", server);
                }
            }

            LOGGER.debug(
                "Selected collector grpc service is not available. Wait {} seconds to retry",
                Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL
            );
        }
    }

    public void addChannelListener(GRPCChannelListener listener) {
        listeners.add(listener);
    }

    public Channel getChannel() {
        return managedChannel.getChannel();
    }

    /**
     * If the given exception is triggered by network problem, connect in background.
     * <pre>
     * （如果给定的异常是由网络问题触发的，请在后台连接。）
     * 监听器通知 Manager ，使用 Channel 时发生的异常。
     * 若是网络异常，则后台进行重连
     * </pre>
     */
    public void reportError(Throwable throwable) {
        // 如果给定的异常是由网络问题触发的
        if (isNetworkError(throwable)) {
            // 打开 重连 开关
            reconnect = true;
            // 通知 GRPCChannelListener，GRPCChannel 的状态 变为 断开
            notify(GRPCChannelStatus.DISCONNECT);
        }
    }

    /**
     * 通知监听器们，Channel 的连接状态
     * @param status 连接状态
     */
    private void notify(GRPCChannelStatus status) {
        for (GRPCChannelListener listener : listeners) {
            try {
                listener.statusChanged(status);
            } catch (Throwable t) {
                LOGGER.error(t, "Fail to notify {} about channel connected.", listener.getClass().getName());
            }
        }
    }

    /**
     * 判断异常是否为网络异常
     * @param throwable
     * @return
     */
    private boolean isNetworkError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
            return statusEquals(
                statusRuntimeException.getStatus(), Status.UNAVAILABLE, Status.PERMISSION_DENIED,
                Status.UNAUTHENTICATED, Status.RESOURCE_EXHAUSTED, Status.UNKNOWN
            );
        }
        return false;
    }

    private boolean statusEquals(Status sourceStatus, Status... potentialStatus) {
        for (Status status : potentialStatus) {
            if (sourceStatus.getCode() == status.getCode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int priority() {
        // 优先级 设置为 最大
        return Integer.MAX_VALUE;
    }
}
