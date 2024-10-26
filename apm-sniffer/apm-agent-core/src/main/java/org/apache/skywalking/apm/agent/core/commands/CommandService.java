/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.agent.core.commands;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.trace.component.command.BaseCommand;
import org.apache.skywalking.apm.network.trace.component.command.CommandDeserializer;
import org.apache.skywalking.apm.network.trace.component.command.UnsupportedCommandException;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

@DefaultImplementor
public class CommandService implements BootService, Runnable {

    private static final ILog LOGGER = LogManager.getLogger(CommandService.class);

    /** 运行开关 */
    private volatile boolean isRunning = true;
    /** this.run 的 执行器 */
    private ExecutorService executorService = Executors.newSingleThreadExecutor(
        new DefaultNamedThreadFactory("CommandService")
    );
    /** 存放 BaseCommand 的 阻塞队列 */
    private LinkedBlockingQueue<BaseCommand> commands = new LinkedBlockingQueue<>(64);
    /** 命令序列号缓存 */
    private CommandSerialNumberCache serialNumberCache = new CommandSerialNumberCache();

    @Override
    public void prepare() throws Throwable {
    }

    @Override
    public void boot() throws Throwable {
        // 向 执行器 提交任务（this）
        executorService.submit(
            new RunnableWithExceptionProtection(this, t -> LOGGER.error(t, "CommandService failed to execute commands"))
        );
    }

    @Override
    public void run() {
        // 从 ServiceManager 获取 CommandExecutorService 服务
        final CommandExecutorService commandExecutorService = ServiceManager.INSTANCE.findService(CommandExecutorService.class);

        // 开关 on 时执行
        while (isRunning) {
            try {
                // 从队列中获取 BaseCommand
                BaseCommand command = commands.take();

                // 根据 command 的序号 判断是否已在 缓存，是则 continue。
                if (isCommandExecuted(command)) {
                    continue;
                }

                // 将 command 提交 给 commandExecutorService 去执行
                commandExecutorService.execute(command);
                // 并将 command 的 序号 加入到 this.serialNumberCache
                serialNumberCache.add(command.getSerialNumber());
            } catch (CommandExecutionException e) {
                LOGGER.error(e, "Failed to execute command[{}].", e.command().getCommand());
            } catch (Throwable e) {
                LOGGER.error(e, "There is unexpected exception");
            }
        }
    }

    /**
     * 已被 execute 给执行器的 command
     */
    private boolean isCommandExecuted(BaseCommand command) {
        return serialNumberCache.contain(command.getSerialNumber());
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        // off
        isRunning = false;
        // 将队列中的 BaseCommand 移到 新列表
        commands.drainTo(new ArrayList<>());
        // 关闭 执行器
        executorService.shutdown();
    }

    /**
     * 接收 Commands
     * @param commands Commands.proto
     */
    public void receiveCommand(Commands commands) {
        for (Command command : commands.getCommandsList()) {
            try {
                // 将 Command.proto 反序列化 为 BaseCommand 对象
                BaseCommand baseCommand = CommandDeserializer.deserialize(command);

                // 若 baseCommand 已被 execute 给执行器，则 continue
                if (isCommandExecuted(baseCommand)) {
                    LOGGER.warn("Command[{}] is executed, ignored", baseCommand.getCommand());
                    continue;
                }

                // 将 baseCommand 入队
                boolean success = this.commands.offer(baseCommand);

                if (!success && LOGGER.isWarnEnable()) {
                    LOGGER.warn(
                        "Command[{}, {}] cannot add to command list. because the command list is full.",
                        baseCommand.getCommand(), baseCommand.getSerialNumber()
                    );
                }
            } catch (UnsupportedCommandException e) {
                if (LOGGER.isWarnEnable()) {
                    LOGGER.warn("Received unsupported command[{}].", e.getCommand().getCommand());
                }
            }
        }
    }
}
