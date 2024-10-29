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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskCommandQuery;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskFinishReport;
import org.apache.skywalking.apm.network.language.profile.v3.ProfileTaskGrpc;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

/**
 * Sniffer and backend, about the communication service of profile task protocol. 1. Sniffer will check has new profile
 * task list every {@link Config.Collector#GET_PROFILE_TASK_INTERVAL} second. 2. When there is a new profile task
 * snapshot, the data is transferred to the back end. use {@link LinkedBlockingQueue} 3. When profiling task finish, it
 * will send task finish status to backend
 *
 * <pre>
 * (嗅探器 和 后端，关于 分析任务 协议 的通信服务。
 *  1. 嗅探器 将 每 20s 检查一次 是否有新的 分析任务列表（20s：{@link Config.Collector#GET_PROFILE_TASK_INTERVAL}）
 *  2. 当有新的 分析任务快照 时，将数据传输到后端。使用 LinkedBlockingQueue
 *  3. 当 分析任务 完成时，它将向 后端 发送 任务完成状态
 *  )
 *
 *  OAP 后台管理端 可以创建 分析任务，
 *  SkyWalking Agent 根据 getProfileTaskCommands() 获取 该Agent需要分析的任务
 *  SkyWalking Agent 根据 reportTaskFinish() 通知 OAP 该分析任务 已完成
 * </pre>
 */
@DefaultImplementor
public class ProfileTaskChannelService implements BootService, Runnable, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(ProfileTaskChannelService.class);

    /** grpc channel 状态 */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    /** 分析任务 的 阻塞式Stub */
    private volatile ProfileTaskGrpc.ProfileTaskBlockingStub profileTaskBlockingStub;

    // segment snapshot sender
    /** 存放 Tracing线程快照 的队列 */
    private final BlockingQueue<TracingThreadSnapshot> snapshotQueue = new LinkedBlockingQueue<>(
        Config.Profile.SNAPSHOT_TRANSPORT_BUFFER_SIZE);
    /** 发送 Tracing线程快照 的结果 凭据 */
    private volatile ScheduledFuture<?> sendSnapshotFuture;

    // query task list schedule
    /** 发送 查询分析任务列表命令 的结果 凭据 */
    private volatile ScheduledFuture<?> getTaskListFuture;

    /** segment快照发送器 */
    private ProfileSnapshotSender sender;

    /**
     * 该任务 定时查询 OAP 后台 新增的 ProfileTask
     * 将 OAP 返回的 Command 交给 ProfileTaskCommandExecutor 执行。
     * 若 查询OAP失败，则打印日志，并 取消 this.getTaskListFuture。
     */
    @Override
    public void run() {
        // 连接中，才发送 查询分析任务列表命令 到 OAP 后端
        if (status == GRPCChannelStatus.CONNECTED) {
            try {
                ProfileTaskCommandQuery.Builder builder = ProfileTaskCommandQuery.newBuilder();

                // sniffer info
                builder.setService(Config.Agent.SERVICE_NAME).setServiceInstance(Config.Agent.INSTANCE_NAME);

                // last command create time
                builder.setLastCommandTime(ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class)
                                                                  .getLastCommandCreateTime());

                Commands commands = profileTaskBlockingStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS) // 设置请求的超时时间
                        // 执行 ProfileTask.service 的 getProfileTaskCommands 方法 获取 该Agent 需要分析的任务
                        .getProfileTaskCommands(builder.build());

                // 将收到的 Commands 传递给 CommandService 进行处理
                ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
            } catch (Throwable t) {
                if (!(t instanceof StatusRuntimeException)) {
                    LOGGER.error(t, "Query profile task from backend fail.");
                    return;
                }
                final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) t;
                if (statusRuntimeException.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                    LOGGER.warn("Backend doesn't support profiling, profiling will be disabled");
                    if (getTaskListFuture != null) {
                        getTaskListFuture.cancel(true);
                    }

                    // stop snapshot sender
                    if (sendSnapshotFuture != null) {
                        sendSnapshotFuture.cancel(true);
                    }
                }
            }
        }
    }

    @Override
    public void prepare() {
        // 将 this 加入到 GRPCChannelManager 的 GRPCChannelListener 中，方便监听 grpc channel 状态变化
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {
        // 从 ServiceManager 获取 segment快照发送器
        sender = ServiceManager.INSTANCE.findService(ProfileSnapshotSender.class);

        // 检查用户是否启用了 分析功能
        if (Config.Profile.ACTIVE) {

            /* 向 OAP 查询 ProfileTask 列表 */
            getTaskListFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ProfileGetTaskService")
            ).scheduleWithFixedDelay(
                new RunnableWithExceptionProtection(
                    this,
                    t -> LOGGER.error("Query profile task list failure.", t)
                ), 0, Config.Collector.GET_PROFILE_TASK_INTERVAL, TimeUnit.SECONDS
            );

            /* 发送 Tracing线程快照 到 OAP */
            sendSnapshotFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ProfileSendSnapshotService")
            ).scheduleWithFixedDelay(
                new RunnableWithExceptionProtection(
                    () -> {
                        List<TracingThreadSnapshot> buffer = new ArrayList<>(Config.Profile.SNAPSHOT_TRANSPORT_BUFFER_SIZE);
                        // 将 snapshotQueue 的数据 移到 buffer
                        snapshotQueue.drainTo(buffer);
                        // 如果 buffer 不为空
                        if (!buffer.isEmpty()) {
                            // 由 ProfileSnapshotSender 发送 Tracing线程快照 到 OAP
                            sender.send(buffer);
                        }
                    },
                    t -> LOGGER.error("Profile segment snapshot upload failure.", t)
                ), 0, 500, TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        // 取消 getTaskListFuture
        if (getTaskListFuture != null) {
            getTaskListFuture.cancel(true);
        }

        // 取消 sendSnapshotFuture
        if (sendSnapshotFuture != null) {
            sendSnapshotFuture.cancel(true);
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            // 当连接成功时，使用 通道 重新 初始化 ProfileTask 的 阻塞式 grpc stub
            profileTaskBlockingStub = ProfileTaskGrpc.newBlockingStub(channel);
        } else {
            // 当连接关闭时，设置 profileTaskBlockingStub 置为 null
            profileTaskBlockingStub = null;
        }
        this.status = status;
    }

    /**
     * add a new profiling snapshot, send to {@link #snapshotQueue}
     * <pre>
     * (添加一个新的 分析快照 ，发送到 snapshotQueue)
     * </pre>
     */
    public void addProfilingSnapshot(TracingThreadSnapshot snapshot) {
        snapshotQueue.offer(snapshot);
    }

    /**
     * notify backend profile task has finish
     * <pre>
     * (通知 后端 分析任务 已完成)
     * </pre>
     *
     * @param task 从 OAP 服务器接收的 ProfileTask
     */
    public void notifyProfileTaskFinish(ProfileTask task) {
        try {
            // 构建 ProfileTaskFinishReport
            final ProfileTaskFinishReport.Builder reportBuilder = ProfileTaskFinishReport.newBuilder();
            // sniffer info
            reportBuilder.setService(Config.Agent.SERVICE_NAME)
                         .setServiceInstance(Config.Agent.INSTANCE_NAME);
            // task info
            reportBuilder.setTaskId(task.getTaskId());

            // send data
            profileTaskBlockingStub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS) // 设置请求的超时时间
                                    // 执行 ProfileTask.service 的 reportTaskFinish 方法，报告 OAP 当前的 ProfileTask 已完成
                                   .reportTaskFinish(reportBuilder.build());
        } catch (Throwable e) {
            LOGGER.error(e, "Notify profile task finish to backend fail.");
        }
    }

}
