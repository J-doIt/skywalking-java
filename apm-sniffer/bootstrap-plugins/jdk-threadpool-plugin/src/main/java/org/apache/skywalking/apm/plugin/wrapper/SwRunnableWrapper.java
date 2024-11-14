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

package org.apache.skywalking.apm.plugin.wrapper;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * 实现 Runnable 接口
 */
public class SwRunnableWrapper implements Runnable {

    /** 被包装的Runnable对象 */
    private Runnable runnable;

    /** 当前上下文的快照 */
    private ContextSnapshot contextSnapshot;

    /**
     * @param runnable 被包装的Runnable对象
     * @param contextSnapshot 当前上下文的快照
     */
    public SwRunnableWrapper(Runnable runnable, ContextSnapshot contextSnapshot) {
        this.runnable = runnable;
        this.contextSnapshot = contextSnapshot;
    }

    @Override
    public void run() {
        // 创建 local span
        AbstractSpan span = ContextManager.createLocalSpan(getOperationName());
        span.setComponent(ComponentsDefine.JDK_THREADING);
        // 将 this.contextSnapshot ref 到当前tracingContext 并继续
        ContextManager.continued(contextSnapshot);
        try {
            // run
            runnable.run();
        } finally {
            // 结束 active span
            ContextManager.stopSpan();
        }
    }

    private String getOperationName() {
        return "SwRunnableWrapper/" + Thread.currentThread().getName();
    }
}
