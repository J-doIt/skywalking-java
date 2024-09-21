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
    /** 定时重连 gRPC Server 的定时任务 */
    private volatile ScheduledFuture<?> connectCheckFuture;
    /**
     * 是否重连。
     * 当 Channel 未连接需要连接，或者 Channel 断开需要重连时，标记 reconnect = true 。后台线程会根据该标识进行连接( 重连 )。
     */
    private volatile boolean reconnect = true;
    private final Random random = new Random();
    /**
     * 监听器数组。
     * 使用 Channel 的其他服务，注册监听器到 GRPCChannelManager 上，
     * 从而根据连接状态( org.skywalking.apm.agent.core.remote.GRPCChannelStatus )，实现自定义逻辑。
     */
    private final List<GRPCChannelListener> listeners = Collections.synchronizedList(new LinkedList<>());
    private volatile List<String> grpcServers;
    private volatile int selectedIdx = -1;
    private volatile int reconnectCount = 0;

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
        if (Config.Collector.BACKEND_SERVICE.trim().length() == 0) {
            LOGGER.error("Collector server addresses are not set.");
            LOGGER.error("Agent will not uplink any data.");
            return;
        }
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
        if (connectCheckFuture != null) {
            connectCheckFuture.cancel(true);
        }
        if (managedChannel != null) {
            managedChannel.shutdownNow();
        }
        LOGGER.debug("Selected collector grpc service shutdown.");
    }

    @Override
    public void run() {
        LOGGER.debug("Selected collector grpc service running, reconnect:{}.", reconnect);
        if (IS_RESOLVE_DNS_PERIODICALLY && reconnect) {
            grpcServers = Arrays.stream(Config.Collector.BACKEND_SERVICE.split(","))
                    .filter(StringUtil::isNotBlank)
                    .map(eachBackendService -> eachBackendService.split(":"))
                    .filter(domainPortPairs -> {
                        if (domainPortPairs.length < 2) {
                            LOGGER.debug("Service address [{}] format error. The expected format is IP:port", domainPortPairs[0]);
                            return false;
                        }
                        return true;
                    })
                    .flatMap(domainPortPairs -> {
                        try {
                            return Arrays.stream(InetAddress.getAllByName(domainPortPairs[0]))
                                    .map(InetAddress::getHostAddress)
                                    .map(ip -> String.format("%s:%s", ip, domainPortPairs[1]));
                        } catch (Throwable t) {
                            LOGGER.error(t, "Failed to resolve {} of backend service.", domainPortPairs[0]);
                        }
                        return Stream.empty();
                    })
                    .distinct()
                    .collect(Collectors.toList());
        }

        // 当 reconnect = true 时，才执行连接( 重连 )
        if (reconnect) {
            // 当本地已经获取到 Collector Agent gRPC Server 集群地址时
            if (grpcServers.size() > 0) {
                String server = "";
                try {
                    // 随机获得准备链接的 Collector Agent gRPC Server
                    int index = Math.abs(random.nextInt()) % grpcServers.size();
                    if (index != selectedIdx) {
                        selectedIdx = index;

                        server = grpcServers.get(index);
                        String[] ipAndPort = server.split(":");

                        if (managedChannel != null) {
                            managedChannel.shutdownNow();
                        }

                        // 创建 Channel ，并连接
                        managedChannel = GRPCChannel.newBuilder(ipAndPort[0], Integer.parseInt(ipAndPort[1]))
                                                    .addManagedChannelBuilder(new StandardChannelBuilder())
                                                    .addManagedChannelBuilder(new TLSChannelBuilder())
                                                    .addChannelDecorator(new AgentIDDecorator())
                                                    .addChannelDecorator(new AuthenticationDecorator())
                                                    .build();
                        reconnectCount = 0;
                        reconnect = false;
                        // 通知 GRPCChannelListener 连接成功
                        notify(GRPCChannelStatus.CONNECTED);
                    } else if (managedChannel.isConnected(++reconnectCount > Config.Agent.FORCE_RECONNECTION_PERIOD)) {
                        // Reconnect to the same server is automatically done by GRPC,
                        // therefore we are responsible to check the connectivity and
                        // set the state and notify listeners
                        reconnectCount = 0;
                        reconnect = false;
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
     * 监听器通知 Manager ，使用 Channel 时发生的异常。
     * 若是网络异常，则后台进行重连
     * </pre>
     */
    public void reportError(Throwable throwable) {
        if (isNetworkError(throwable)) {
            reconnect = true;
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
        return Integer.MAX_VALUE;
    }
}
