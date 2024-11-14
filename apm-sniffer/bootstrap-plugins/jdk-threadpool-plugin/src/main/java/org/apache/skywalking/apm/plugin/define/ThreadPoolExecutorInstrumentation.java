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

package org.apache.skywalking.apm.plugin.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.HierarchyMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation;

/**
 * <pre>
 * 增强类：
 *      java.util.concurrent.ThreadPoolExecutor 和 其子类或实现类
 * 增强方法（重写参数）：
 *          void execute(Runnable command)
 *      拦截器：org.apache.skywalking.apm.plugin.ThreadPoolExecuteMethodInterceptor
 * 增强方法（重写参数）：
 *          ≤T> Future≤T> submit(Callable≤T> task)
 *          Future≤?> submit(Runnable task)
 *          ≤T> Future≤T> submit(Runnable task, T result)
 *      拦截器：org.apache.skywalking.apm.plugin.ThreadPoolSubmitMethodInterceptor
 * </pre>
 */
public class ThreadPoolExecutorInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "java.util.concurrent.ThreadPoolExecutor";

    private static final String INTERCEPT_EXECUTE_METHOD = "execute";

    private static final String INTERCEPT_SUBMIT_METHOD = "submit";

    private static final String INTERCEPT_EXECUTE_METHOD_HANDLE = "org.apache.skywalking.apm.plugin.ThreadPoolExecuteMethodInterceptor";

    private static final String INTERCEPT_SUBMIT_METHOD_HANDLE = "org.apache.skywalking.apm.plugin.ThreadPoolSubmitMethodInterceptor";

    @Override
    public boolean isBootstrapInstrumentation() {
        // 被 启动类加载器 加载
        return true;
    }

    @Override
    protected ClassMatch enhanceClass() {
        return LogicalMatchOperation.or(
                HierarchyMatch.byHierarchyMatch(ENHANCE_CLASS), // java.util.concurrent.ThreadPoolExecutor 的子类或实现类
                MultiClassNameMatch.byMultiClassMatch(ENHANCE_CLASS)); // java.util.concurrent.ThreadPoolExecutor
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.named(INTERCEPT_EXECUTE_METHOD);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return INTERCEPT_EXECUTE_METHOD_HANDLE;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.named(INTERCEPT_SUBMIT_METHOD);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return INTERCEPT_SUBMIT_METHOD_HANDLE;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                }
        };
    }
}
