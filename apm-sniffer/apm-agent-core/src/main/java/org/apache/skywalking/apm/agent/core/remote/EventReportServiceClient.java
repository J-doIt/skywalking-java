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
import io.grpc.stub.StreamObserver;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.apm.network.event.v3.EventServiceGrpc;
import org.apache.skywalking.apm.network.event.v3.Source;
import org.apache.skywalking.apm.network.event.v3.Type;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;
import static org.apache.skywalking.apm.agent.core.conf.Constants.EVENT_LAYER_NAME;
import static org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus.CONNECTED;

/**
 * 事件报告服务客户端
 */
@DefaultImplementor
public class EventReportServiceClient implements BootService, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(EventReportServiceClient.class);

    /** startingEvent 已发送给了 OAP */
    private final AtomicBoolean reported = new AtomicBoolean();

    /** Event 协议 的 构造器 */
    private Event.Builder startingEvent;

    /** EventService 的 Stub */
    private EventServiceGrpc.EventServiceStub eventServiceStub;

    /** grpc channel 状态 */
    private GRPCChannelStatus status;

    @Override
    public void prepare() throws Throwable {
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中，方便监听 grpc channel 状态变化
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);

        // 获取 JVM 的运行时系统的 托管Bean。
        final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        // 创建 Event.Builder
        startingEvent = Event.newBuilder()
                             .setUuid(UUID.randomUUID().toString())
                             .setName("Start") // 事件名称
                             .setStartTime(runtimeMxBean.getStartTime())
                             .setMessage("Start Java Application") // 事件消息
                             .setType(Type.Normal)
                             .setSource(
                                 Source.newBuilder()
                                       .setService(Config.Agent.SERVICE_NAME)
                                       .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                       .build()
                             )
                             .putParameters(
                                 "OPTS",
                                 runtimeMxBean.getInputArguments()
                                              .stream()
                                              .sorted()
                                              .collect(Collectors.joining(" "))
                             )
                             .setLayer(EVENT_LAYER_NAME);
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {
        // 为 Event.Builder 设置 结束时间
        startingEvent.setEndTime(System.currentTimeMillis());

        // 发送 starting Event 到 OAP
        reportStartingEvent();
    }

    /**
     * grpc通道未关闭，则构建 shutdown Event，并发送到 OAP。
     */
    @Override
    public void shutdown() throws Throwable {
        // grpc通道未连接，直接返回
        if (!CONNECTED.equals(status)) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final Event.Builder shutdownEvent = Event.newBuilder()
                                                 .setUuid(UUID.randomUUID().toString())
                                                 .setName("Shutdown") // 事件名称
                                                 .setStartTime(System.currentTimeMillis())
                                                 .setEndTime(System.currentTimeMillis())
                                                 .setMessage("Shutting down Java Application") // 事件消息
                                                 .setType(Type.Normal)
                                                 .setSource(
                                                     Source.newBuilder()
                                                           .setService(Config.Agent.SERVICE_NAME)
                                                           .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                                           .build()
                                                 )
                                                .setLayer(EVENT_LAYER_NAME);

        // 声明 grpc 调用
        final StreamObserver<Event> collector = eventServiceStub.collect(new StreamObserver<Commands>() {
            @Override
            public void onNext(final Commands commands) {
                // 将 Commands 交给 CommandService 去执行
                ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.error("Failed to report shutdown event.", t);
                // Ignore status change at shutting down stage.
                // 释放 latch
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                // 释放 latch
                latch.countDown();
            }
        });

        // 使用 StreamObserver 发送 shutdown Event 到 OAP
        collector.onNext(shutdownEvent.build());
        // shutdown Event 发送完毕后，通知 Collector 数据传输已完成
        collector.onCompleted();
        // 等待，知道另一个线程释放 latch
        latch.await();
    }

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        // 更新grpc通道状态
        this.status = status;

        // grpc通道未连接，直接返回
        if (!CONNECTED.equals(status)) {
            return;
        }

        final Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
        // 当连接成功时，使用 通道 重新 初始化 EventService 的 Stub
        eventServiceStub = EventServiceGrpc.newStub(channel);
        // 设置 stub（grpc） 的截止时间
        eventServiceStub = eventServiceStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS);

        // 发送 starting Event 到 OAP
        reportStartingEvent();
    }

    /**
     * 发送 starting Event 到 OAP
     */
    private void reportStartingEvent() {
        // 将 reported 从 false 设置为 true 时失败，则返回
        if (reported.compareAndSet(false, true)) {
            return;
        }

        // 声明 grpc 调用
        final StreamObserver<Event> collector = eventServiceStub.collect(new StreamObserver<Commands>() {
            @Override
            public void onNext(final Commands commands) {
                // 将 Commands 交给 CommandService 去执行
                ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.error("Failed to report starting event.", t);
                // 发送到 OAP 后端 失败，向 GRPCChannelManager 报告 错误
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
                // 设置 reported 为 false
                reported.set(false);
            }

            @Override
            public void onCompleted() {
            }
        });

        // 使用 StreamObserver 发送 starting Event 到 OAP
        collector.onNext(startingEvent.build());
        // starting Event 发送完毕后，通知 Collector 数据传输已完成
        collector.onCompleted();
    }
}
