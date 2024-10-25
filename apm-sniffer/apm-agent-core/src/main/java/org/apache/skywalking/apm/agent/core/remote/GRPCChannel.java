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
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import java.util.LinkedList;
import java.util.List;

public class GRPCChannel {
    /**
     * origin channel（经过 装饰前的通道）
     */
    private final ManagedChannel originChannel;
    /** 装饰后的通道 */
    private final Channel channelWithDecorators;

    /**
     * @param host 主机名
     * @param port 端口
     * @param channelBuilders SK 定义的 ManagedChannelBuilder 构建器
     * @param decorators SK 定义的 通道装饰器
     */
    private GRPCChannel(String host, int port, List<ChannelBuilder> channelBuilders,
                        List<ChannelDecorator> decorators) throws Exception {
        // 创建 ManagedChannelBuilder，并指定主机和端口
        ManagedChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(host, port);

        // 注册默认的 DNS 解析器提供者
        NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());

        // 遍历 ManagedChannelBuilder 构建器列表，应用每个构建器来配置 通道
        for (ChannelBuilder builder : channelBuilders) {
            // 构建 ManagedChannelBuilder
            channelBuilder = builder.build(channelBuilder);
        }

        // 构建 ManagedChannel
        this.originChannel = channelBuilder.build();

        Channel channel = originChannel;

        // 遍历通道装饰器列表，应用每个装饰器来增强通道功能
        for (ChannelDecorator decorator : decorators) {
            channel = decorator.build(channel);
        }

        // 将经过装饰的通道赋值给 channelWithDecorators
        channelWithDecorators = channel;
    }

    public static Builder newBuilder(String host, int port) {
        return new Builder(host, port);
    }

    public Channel getChannel() {
        return this.channelWithDecorators;
    }

    public boolean isTerminated() {
        return originChannel.isTerminated();
    }

    public void shutdownNow() {
        originChannel.shutdownNow();
    }

    public boolean isShutdown() {
        return originChannel.isShutdown();
    }

    public boolean isConnected() {
        return isConnected(false);
    }

    public boolean isConnected(boolean requestConnection) {
        return originChannel.getState(requestConnection) == ConnectivityState.READY;
    }

    public static class Builder {
        private final String host;
        private final int port;
        private final List<ChannelBuilder> channelBuilders;
        private final List<ChannelDecorator> decorators;

        private Builder(String host, int port) {
            this.host = host;
            this.port = port;
            this.channelBuilders = new LinkedList<>();
            this.decorators = new LinkedList<>();
        }

        public Builder addChannelDecorator(ChannelDecorator interceptor) {
            this.decorators.add(interceptor);
            return this;
        }

        public GRPCChannel build() throws Exception {
            return new GRPCChannel(host, port, channelBuilders, decorators);
        }

        public Builder addManagedChannelBuilder(ChannelBuilder builder) {
            channelBuilders.add(builder);
            return this;
        }
    }
}
