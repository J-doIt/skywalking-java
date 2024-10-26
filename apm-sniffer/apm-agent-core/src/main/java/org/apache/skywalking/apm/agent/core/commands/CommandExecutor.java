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

import org.apache.skywalking.apm.network.trace.component.command.BaseCommand;

/**
 * Command executor that can handle a given command, implementations are required to be stateless, i.e. the previous
 * execution of a command cannot affect the next execution of another command.
 * <pre>
 * (可以处理给定命令的命令执行器，其实现要求是<b>无状态的</b>，即<b>命令的前一个执行不能影响另一个命令的下一个执行</b>。)
 * </pre>
 */
public interface CommandExecutor {
    /**
     * @param command 要执行的命令
     * @throws CommandExecutionException when the executor failed to execute the command
     */
    void execute(BaseCommand command) throws CommandExecutionException;
}
