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

package org.apache.skywalking.apm.plugin;

import org.apache.skywalking.apm.plugin.wrapper.SwRunnableWrapper;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import java.util.concurrent.RunnableFuture;

/**
 * <pre>
 * 增强类：
 *      java.util.concurrent.ThreadPoolExecutor 和 其子类或实现类
 * 增强方法（重写参数）：
 *          void execute(Runnable command)
 * </pre>
 */
public class ThreadPoolExecuteMethodInterceptor extends AbstractThreadingPoolInterceptor {

    @Override
    public Object wrap(Object param) {
        // 如果 param 已经被包装过，返回 null 表示不再包装
        if (param instanceof SwRunnableWrapper) {
            return null;
        }

        // 如果 param 是 RunnableFuture，返回 null 表示不再包装
        if (param instanceof RunnableFuture) {
            return null;
        }

        // 如果 param 不是 Runnable，返回 null 表示不再包装
        if (!(param instanceof Runnable)) {
            return null;
        }

        Runnable runnable = (Runnable) param;
        // 将 Runnable 包装成 SwRunnableWrapper，并返回
        return new SwRunnableWrapper(runnable, ContextManager.capture()/* 捕获当前上下文的快照 */);
    }

}
