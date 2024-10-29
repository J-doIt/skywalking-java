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

package org.apache.skywalking.apm.agent.core.conf.dynamic;

import com.google.common.collect.Lists;
import io.grpc.Channel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.network.language.agent.v3.ConfigurationDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.ConfigurationSyncRequest;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.trace.component.command.ConfigurationDiscoveryCommand;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

@DefaultImplementor
public class ConfigurationDiscoveryService implements BootService, GRPCChannelListener {

    /**
     * UUID of the last return value.
     * 最后一个 响应的 uuid
     */
    private String uuid;
    /** 本地 动态配置中心 */
    private final Register register = new Register();

    private volatile int lastRegisterWatcherSize;

    /** 从 OAP 获取服务的最新动态配置 的 结果 的 凭证 */
    private volatile ScheduledFuture<?> getDynamicConfigurationFuture;
    /** grpc channel 状态 */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    /** ConfigurationDiscoveryService 的 Stub */
    private volatile ConfigurationDiscoveryServiceGrpc.ConfigurationDiscoveryServiceBlockingStub configurationDiscoveryServiceBlockingStub;

    private static final ILog LOGGER = LogManager.getLogger(ConfigurationDiscoveryService.class);

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            // 当连接成功时，使用 通道 重新 初始化 ConfigurationDiscoveryService 的 Stub
            configurationDiscoveryServiceBlockingStub = ConfigurationDiscoveryServiceGrpc.newBlockingStub(channel);
        } else {
            // 当连接关闭时，设置 configurationDiscoveryServiceBlockingStub 置为 null
            configurationDiscoveryServiceBlockingStub = null;
        }
        // 更新grpc通道的状态
        this.status = status;
    }

    @Override
    public void prepare() throws Throwable {
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中，方便监听 grpc channel 状态变化
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        // 定时任务，20s后，每 20s 执行一次 getAgentDynamicConfig()
        getDynamicConfigurationFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("ConfigurationDiscoveryService")
        ).scheduleAtFixedRate(
            new RunnableWithExceptionProtection(
                this::getAgentDynamicConfig,
                t -> LOGGER.error("Sync config from OAP error.", t)
            ),
            Config.Collector.GET_AGENT_DYNAMIC_CONFIG_INTERVAL,
            Config.Collector.GET_AGENT_DYNAMIC_CONFIG_INTERVAL,
            TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        // 取消 getDynamicConfigurationFuture
        if (getDynamicConfigurationFuture != null) {
            getDynamicConfigurationFuture.cancel(true);
        }
    }

    /**
     * Register dynamic configuration watcher.
     *
     * @param watcher dynamic configuration watcher
     */
    public synchronized void registerAgentConfigChangeWatcher(AgentConfigChangeWatcher watcher) {
        WatcherHolder holder = new WatcherHolder(watcher);
        if (register.containsKey(holder.getKey())) {
            List<WatcherHolder> watcherHolderList = register.get(holder.getKey());
            for (WatcherHolder watcherHolder : watcherHolderList) {
                if (watcherHolder.getWatcher().getClass().equals(watcher.getClass())) {
                    LOGGER.debug("Duplicate register, watcher={}", watcher);
                    return;
                }
            }
        }
        register.put(holder.getKey(), holder);
    }

    /**
     * Process ConfigurationDiscoveryCommand and notify each configuration watcher.
     * （处理 ConfigurationDiscoveryCommand ，并通知每个配置观察程序。）
     *
     * @param configurationDiscoveryCommand Describe dynamic configuration information
     */
    public void handleConfigurationDiscoveryCommand(ConfigurationDiscoveryCommand configurationDiscoveryCommand) {
        final String responseUuid = configurationDiscoveryCommand.getUuid();

        // 如果响应 uuid 和 this.uuid 相同，则说明 config 保持不变
        if (responseUuid != null && Objects.equals(this.uuid, responseUuid)) {
            return;
        }

        // 读取OAP返回的动态配置
        List<KeyStringValuePair> config = readConfig(configurationDiscoveryCommand);

        config.forEach(property -> {
            String propertyKey = property.getKey();
            // 根据 propertyKey 获取 对应的 观察者们
            List<WatcherHolder> holderList = register.get(propertyKey);
            for (WatcherHolder holder : holderList) {
                if (holder != null) {
                    AgentConfigChangeWatcher watcher = holder.getWatcher();
                    String newPropertyValue = property.getValue();
                    // 如果 新值 为 null
                    if (StringUtil.isBlank(newPropertyValue)) {
                        if (watcher.value() != null) {
                            // Notify watcher, the new value is null with delete event type.
                            // (通知观察者，新值为 null，事件类型为 delete。)
                            watcher.notify(
                                    new AgentConfigChangeWatcher.ConfigChangeEvent(
                                            null, AgentConfigChangeWatcher.EventType.DELETE
                                    ));
                        } else {
                            // Don't need to notify, stay in null.
                            // (如果 旧值 为 null，无需通知，保持 null。)
                        }
                    } else { // 如果 新值 不为 null
                        //  如果 新增 != 旧值
                        if (!newPropertyValue.equals(watcher.value())) {
                            // 通知观察者，新值为 newPropertyValue，事件类型为 modify。
                            watcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
                                    newPropertyValue, AgentConfigChangeWatcher.EventType.MODIFY
                            ));
                        } else {
                            // Don't need to notify, stay in the same config value.
                            // (如果 新增 == 旧值，不需要通知，保持相同的 config 值。)
                        }
                    }
                } else {
                    // 匹配任何 watcher，忽略
                    LOGGER.warn("Config {} from OAP, doesn't match any watcher, ignore.", propertyKey);
                }
            }
        });
        // 更新 uuid 为 响应的uuid
        this.uuid = responseUuid;

        LOGGER.trace("Current configurations after the sync, configurations:{}", register.toString());
    }

    /**
     * Read the registered dynamic configuration, compare it with the dynamic configuration information returned by the
     * service, and complete the dynamic configuration that has been deleted on the OAP.
     * <pre>
     * (读取注册的动态配置，与服务返回的动态配置信息进行比较，完成OAP上已删除的动态配置。)
     * </pre>
     *
     * @param configurationDiscoveryCommand Describe dynamic configuration information
     * @return Adapted dynamic configuration information
     */
    private List<KeyStringValuePair> readConfig(ConfigurationDiscoveryCommand configurationDiscoveryCommand) {
        Map<String, KeyStringValuePair> commandConfigs = configurationDiscoveryCommand.getConfig()
                                                                                      .stream()
                                                                                      .collect(Collectors.toMap(
                                                                                          KeyStringValuePair::getKey,
                                                                                          Function.identity()
                                                                                      ));
        List<KeyStringValuePair> configList = Lists.newArrayList();
        for (final String name : register.keys()) {
            KeyStringValuePair command = commandConfigs.getOrDefault(name, KeyStringValuePair.newBuilder()
                                                                                             .setKey(name)
                                                                                             .build());
            configList.add(command);
        }
        return configList;
    }

    /**
     * get agent dynamic config through gRPC.
     * (通过 gRPC 获取代理动态配置。)
     */
    private void getAgentDynamicConfig() {
        LOGGER.debug("ConfigurationDiscoveryService running, status:{}.", status);

        // 连接中，才发送 ConfigurationSyncRequest 到 OAP 后端
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            try {
                ConfigurationSyncRequest.Builder builder = ConfigurationSyncRequest.newBuilder();
                builder.setService(Config.Agent.SERVICE_NAME);

                // Some plugin will register watcher later.
                final int size = register.getRegisterWatcherSize();
                if (lastRegisterWatcherSize != size) {
                    // reset uuid, avoid the same uuid causing the configuration not to be updated.
                    uuid = null;
                    lastRegisterWatcherSize = size;
                }

                if (null != uuid) {
                    builder.setUuid(uuid);
                }

                if (configurationDiscoveryServiceBlockingStub != null) {
                    final Commands commands = configurationDiscoveryServiceBlockingStub
                            .withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS) // 设置请求的超时时间
                            .fetchConfigurations(builder.build()); // 调用 grpc 获取服务的最新动态配置
                    // 将 Commands 交给 CommandService 去执行
                    ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "ConfigurationDiscoveryService execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }

    /**
     * Local dynamic configuration center.
     * <pre>
     * (本地 动态配置中心。)
     * </pre>
     */
    public static class Register {
        /** ≤ propertyKey, WatcherHolder 的 List ≥ */
        private final Map<String, List<WatcherHolder>> register = new HashMap<>();

        private boolean containsKey(String key) {
            return register.containsKey(key);
        }

        private void put(String key, WatcherHolder holder) {
            List<WatcherHolder> watcherHolderList = register.get(key);
            if (CollectionUtil.isEmpty(watcherHolderList)) {
                ArrayList<WatcherHolder> newWatchHolderList = new ArrayList<>();
                newWatchHolderList.add(holder);
                register.put(key, newWatchHolderList);
            } else {
                watcherHolderList.add(holder);
                register.put(key, watcherHolderList);
            }
        }

        /**
         * @param name propertyKey
         */
        public List<WatcherHolder> get(String name) {
            return register.get(name);
        }

        public Set<String> keys() {
            return register.keySet();
        }

        public int getRegisterWatcherSize() {
            return register.values().stream()
                    .mapToInt(List::size)
                    .sum();
        }

        @Override
        public String toString() {
            ArrayList<String> registerTableDescription = new ArrayList<>(register.size());
            register.forEach((key, holderList) -> {
                for (WatcherHolder holder : holderList) {
                    registerTableDescription.add(new StringBuilder().append("key:")
                            .append(key)
                            .append("value(current):")
                            .append(holder.getWatcher().value()).toString());
                }
            });
            return registerTableDescription.stream().collect(Collectors.joining(",", "[", "]"));
        }
    }

    /** 配置变更观察者的持有者 */
    @Getter
    private static class WatcherHolder {
        /** 配置变更观察者 */
        private final AgentConfigChangeWatcher watcher;
        private final String key;

        public WatcherHolder(AgentConfigChangeWatcher watcher) {
            this.watcher = watcher;
            this.key = watcher.getPropertyKey();
        }
    }
}
