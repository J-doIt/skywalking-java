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

package org.apache.skywalking.apm.agent.core.meter;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.agent.core.remote.GRPCStreamServiceStatus;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * MeterSender collects the values of registered meter instances, and sends to the backend.
 * <pre>
 * (MeterSender 收集 已注册的 meter实例 的值，并发送到后端。)
 * </pre>
 */
@DefaultImplementor
public class MeterSender implements BootService, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(MeterSender.class);

    /** grpc channel 状态 */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    /** meter报告服务 的 grpc stub */
    private volatile MeterReportServiceGrpc.MeterReportServiceStub meterReportServiceStub;

    @Override
    public void prepare() {
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中，方便监听 grpc channel 状态变化
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {

    }

    /**
     * @param meterMap 通过 MeterService 注册的 BaseMeters
     */
    public void send(Map<MeterId, BaseMeter> meterMap, MeterService meterService) {
        // 连接中，才发送 meter 到 OAP 后端
        if (status == GRPCChannelStatus.CONNECTED) {
            StreamObserver<MeterData> reportStreamObserver = null;
            final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
            try {
                // 使用 grpc 存根调用远程方法
                reportStreamObserver = meterReportServiceStub
                        .withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS) // 设置请求的超时时间
                        // 声明 StreamObserver<Commands>，执行 stub 的 collect()。
                        .collect(new StreamObserver<Commands>() {
                            @Override
                            public void onNext(Commands commands) {
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                status.finished(); // 标记流为已完成
                                if (LOGGER.isErrorEnable()) {
                                    LOGGER.error(throwable, "Send meters to collector fail with a grpc internal exception.");
                                }
                                // 发送到 OAP 后端 失败，向 GRPCChannelManager 报告 错误
                                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(throwable);
                            }

                            @Override
                            public void onCompleted() {
                                status.finished(); // 标记流为已完成
                            }
                        });

                final StreamObserver<MeterData> reporter = reportStreamObserver;
                // 转换 meterMap 为 proto 格式，并声明 Consumer 函数实现（使用 StreamObserver 发送 转换后的 meter 到 OAP）
                transform(meterMap, meterData -> reporter.onNext(meterData));
            } catch (Throwable e) {
                if (!(e instanceof StatusRuntimeException)) {
                    LOGGER.error(e, "Report meters to backend fail.");
                    return;
                }
                final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) e;
                if (statusRuntimeException.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                    LOGGER.warn("Backend doesn't support meter, it will be disabled");

                    meterService.shutdown();
                }
            } finally {
                if (reportStreamObserver != null) {
                    reportStreamObserver.onCompleted();
                }
                status.wait4Finish();
            }
        }
    }

    /**
     * @param meterMap 转换前的 meter
     * @param consumer 发送 转换后的 meter 到 OAP 的函数实现
     */
    protected void transform(final Map<MeterId, BaseMeter> meterMap,
                             final Consumer<MeterData> consumer) {
        // build and report meters
        boolean hasSendMachineInfo = false;
        for (BaseMeter meter : meterMap.values()) {
            // 将 meter 转换为 proto 格式
            final MeterData.Builder dataBuilder = meter.transform();
            if (dataBuilder == null) {
                continue;
            }

            // only send the service base info at the first data
            // (仅在第一个数据处 发送 Service Base Info)
            if (!hasSendMachineInfo) {
                dataBuilder.setService(Config.Agent.SERVICE_NAME);
                dataBuilder.setServiceInstance(Config.Agent.INSTANCE_NAME);
                dataBuilder.setTimestamp(System.currentTimeMillis());
                hasSendMachineInfo = true;
            }

            // 将 meter 发送到 OAP
            consumer.accept(dataBuilder.build());
        }
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            // 当连接成功时，使用 通道 重新 初始化 MeterReportService 的 grpc stub
            meterReportServiceStub = MeterReportServiceGrpc.newStub(channel);
        } else {
            // 当连接关闭时，设置 MeterReportService 为 null
            meterReportServiceStub = null;
        }
        // 更新grpc通道状态
        this.status = status;
    }
}
