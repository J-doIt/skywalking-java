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

package org.apache.skywalking.apm.agent.core.profile;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.remote.GRPCStreamServiceStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskGrpc;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * send segment snapshot
 *
 * <pre>
 * (segment快照发送器)
 * </pre>
 */
@DefaultImplementor
public class ProfileSnapshotSender implements BootService, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(ProfileSnapshotSender.class);

    /** grpc channel 状态 */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    /** 分析任务 的 Stub */
    private volatile ProfileTaskGrpc.ProfileTaskStub profileTaskStub;

    @Override
    public void prepare() throws Throwable {
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中，方便监听 grpc channel 状态变化
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            // 当连接成功时，使用 通道 重新 初始化 ProfileTask 的 Stub
            profileTaskStub = ProfileTaskGrpc.newStub(channel);
        } else {
            // 当连接关闭时，设置 profileTaskStub 置为 null
            profileTaskStub = null;
        }
        this.status = status;
    }

    /**
     *
     * @param buffer TracingThreadSnapshot
     */
    public void send(List<TracingThreadSnapshot> buffer) {
        // 连接中，才发送 Tracing线程快照 到 OAP 后端
        if (status == GRPCChannelStatus.CONNECTED) {
            try {
                final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
                StreamObserver<ThreadSnapshot> snapshotStreamObserver = profileTaskStub
                        .withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS) // 设置请求的超时时间
                        // 执行 ProfileTask.service 的 collectSnapshot 方法
                        .collectSnapshot(
                            new StreamObserver<Commands>() {
                                @Override
                                public void onNext(
                                    Commands commands) {
                                }

                                @Override
                                public void onError(
                                    Throwable throwable) {
                                    // grpc stream 状态置为 true（结束）
                                    status.finished();
                                    if (LOGGER.isErrorEnable()) {
                                        LOGGER.error(
                                            throwable,
                                            "Send profile segment snapshot to collector fail with a grpc internal exception."
                                        );
                                    }
                                    // 发送到 OAP 后端 失败，向 GRPCChannelManager 报告 错误
                                    ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(throwable);
                                }

                                @Override
                                public void onCompleted() {
                                    // grpc stream 状态置为 true（结束）
                                    status.finished();
                                }
                            }
                        );
                for (TracingThreadSnapshot snapshot : buffer) {
                    // 转换为 gRPC 数据
                    final ThreadSnapshot transformSnapshot = snapshot.transform();
                    // 通过 grpc stream 发送数据 到 OAP
                    snapshotStreamObserver.onNext(transformSnapshot);
                }

                // stream 完成
                snapshotStreamObserver.onCompleted();
                // wait 直到 stream 完成
                status.wait4Finish();
            } catch (Throwable t) {
                LOGGER.error(t, "Send profile segment snapshot to backend fail.");
            }
        }
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }
}