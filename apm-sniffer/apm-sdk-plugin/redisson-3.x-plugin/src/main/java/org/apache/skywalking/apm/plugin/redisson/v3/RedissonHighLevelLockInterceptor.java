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

package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * 增强类：
 *      org.redisson.RedissonLock 的子类或实现类
 *      或
 *      org.redisson.RedissonLock
 * 增强方法：
 *      ≤T> RFuture≤T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand≤T> command)
 * </pre>
 */
public class RedissonHighLevelLockInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        // 创建 操作名为 Redisson/LOCK 的 local span
        AbstractSpan span = ContextManager.createLocalSpan("Redisson/LOCK");
        span.setComponent(ComponentsDefine.REDISSON);
        SpanLayer.asCache(span);
        // RLock 是 RedissonLock 的父类
        RLock rLock = (RLock) objInst;
        Tags.LOCK_NAME.set(span, rLock.getName());
        Tags.CACHE_TYPE.set(span, "Redis");
        TimeUnit unit = (TimeUnit) allArguments[2];
        Tags.LEASE_TIME.set(span, String.valueOf(unit.toMillis((Long) allArguments[1])));
        Tags.THREAD_ID.set(span, String.valueOf(allArguments[3]));
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        AbstractSpan span = ContextManager.activeSpan();
        // 为 当前tracingContext 开启异步模式
        span.prepareForAsync();
        // 结束 active span
        ContextManager.stopSpan();

        RFuture<Object> future = (RFuture) ret;
        CompletableFuture<Object> completableFuture = future.toCompletableFuture();
        completableFuture.whenComplete((res, ex) -> {
            // 异步线程执行的操作

            if (ex != null) {
                // 设置 span 的 errorOccurred 标志位为 true
                span.errorOccurred();
                // 设置 span 的 log 为 ex
                span.log(ex);
            }
            // 异步span 结束，完成此上下文（异步span非当前span）
            span.asyncFinish();
        });
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        // 设置 span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }
}
