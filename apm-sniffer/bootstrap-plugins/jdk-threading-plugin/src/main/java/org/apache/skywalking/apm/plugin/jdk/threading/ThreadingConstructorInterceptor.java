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

package org.apache.skywalking.apm.plugin.jdk.threading;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

/**
 * <pre>
 * 增强类：
 *      java.util.concurrent.Callable 以及子类，且配置在了 ‘plugin.jdkthreading.threading_class_prefixes’ 中的类
 *      or
 *      java.lang.Runnable 以及子类，且配置在了 ‘plugin.jdkthreading.threading_class_prefixes’ 中的类
 * 增强构造函数：
 *          any
 * </pre>
 */
public class ThreadingConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(final EnhancedInstance objInst, final Object[] allArguments) {
        // 如果 当前tracingContext 中存在 active span
        if (ContextManager.isActive()) {
            // 将 当前tracingContext快照 放入到 objInst 的增强域
            objInst.setSkyWalkingDynamicField(ContextManager.capture());
        }
    }

}
