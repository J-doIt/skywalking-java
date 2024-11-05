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

package org.apache.skywalking.apm.plugin.redisson.v3.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.HierarchyMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * <pre>
 * 增强类：
 *      org.redisson.RedissonLock 及其子类
 *      或
 *      org.redisson.RedissonSpinLock
 * 增强方法：
 *      RedissonSpinLock：
 *          ≤T> RFuture≤T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand≤T> command)
 *      拦截器：org.apache.skywalking.apm.plugin.redisson.v3.RedissonLockInterceptor
 * 增强方法：
 *      RedissonLock：
 *          ≤T> RFuture≤T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand≤T> command)
 *      拦截器：org.apache.skywalking.apm.plugin.redisson.v3.RedissonHighLevelLockInterceptor
 * </pre>
 */
public class RedissonLockInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String REDISSON_LOCK_CLASS = "org.redisson.RedissonLock";

    private static final String REDISSON_SPIN_LOCK_CLASS = "org.redisson.RedissonSpinLock";

    private static final String REDISSON_LOCK_INTERCEPTOR = "org.apache.skywalking.apm.plugin.redisson.v3.RedissonLockInterceptor";

    private static final String REDISSON_HIGH_LEVEL_LOCK_INTERCEPTOR = "org.apache.skywalking.apm.plugin.redisson.v3.RedissonHighLevelLockInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return LogicalMatchOperation.or( // “或”，满足任何一个条件，则匹配成功
                HierarchyMatch.byHierarchyMatch(REDISSON_LOCK_CLASS), // 匹配 RedissonLock 类及其子类
                MultiClassNameMatch.byMultiClassMatch(REDISSON_LOCK_CLASS, REDISSON_SPIN_LOCK_CLASS) // 匹配 RedissonLock 和 RedissonSpinLock 具体类
        );
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
                        return named("tryLockInnerAsync").and(takesArgumentWithType(1, "java.util.concurrent.TimeUnit"));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return REDISSON_LOCK_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("tryLockInnerAsync").and(takesArgumentWithType(2, "java.util.concurrent.TimeUnit"));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return REDISSON_HIGH_LEVEL_LOCK_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
}
