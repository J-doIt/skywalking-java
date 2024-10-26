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

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.commands.executor.ConfigurationDiscoveryCommandExecutor;
import org.apache.skywalking.apm.agent.core.commands.executor.NoopCommandExecutor;
import org.apache.skywalking.apm.agent.core.commands.executor.ProfileTaskCommandExecutor;
import org.apache.skywalking.apm.network.trace.component.command.BaseCommand;
import org.apache.skywalking.apm.network.trace.component.command.ConfigurationDiscoveryCommand;
import org.apache.skywalking.apm.network.trace.component.command.ProfileTaskCommand;

/**
 * Command executor service, acts like a routing executor that controls all commands' execution, is responsible for
 * managing all the mappings between commands and their executors, one can simply invoke {@link #execute(BaseCommand)}
 * and it will routes the command to corresponding executor.
 * <p>
 * Registering command executor for new command in {@link #commandExecutorMap} is required to support new command.
 *
 * <pre>
 * (命令执行器服务，
 * 就像一个 路由执行器，控制所有命令的执行，负责管理命令和它们的执行器之间的所有映射，
 * 可以简单地调用 execute(BaseCommand)，它将命令路由到相应的执行器。
 * 为支持新命令，需要在 commandExecutorMap 中为 新命令 注册 命令执行器。)
 * </pre>
 */
@DefaultImplementor
public class CommandExecutorService implements BootService, CommandExecutor {
    /** ≤命令，执行器≥ */
    private Map<String, CommandExecutor> commandExecutorMap;

    @Override
    public void prepare() throws Throwable {
        commandExecutorMap = new HashMap<String, CommandExecutor>();

        // Profile task executor
        // 分析任务命令执行程序
        commandExecutorMap.put(ProfileTaskCommand.NAME, new ProfileTaskCommandExecutor());

        //Get ConfigurationDiscoveryCommand executor.
        // 配置发现命令执行程序
        commandExecutorMap.put(ConfigurationDiscoveryCommand.NAME, new ConfigurationDiscoveryCommandExecutor());
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }

    @Override
    public void execute(final BaseCommand command) throws CommandExecutionException {
        // 由各自的执行器执行命令
        executorForCommand(command).execute(command);
    }

    private CommandExecutor executorForCommand(final BaseCommand command) {
        final CommandExecutor executor = commandExecutorMap.get(command.getCommand());
        if (executor != null) {
            return executor;
        }
        return NoopCommandExecutor.INSTANCE;
    }
}
