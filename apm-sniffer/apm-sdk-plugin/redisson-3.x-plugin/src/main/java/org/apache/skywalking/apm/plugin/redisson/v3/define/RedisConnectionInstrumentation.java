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
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.ClassInstanceMethodsEnhancePluginDefineV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.v2.InstanceMethodsInterceptV2Point;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * <pre>
 * 增强类：org.redisson.client.RedisConnection
 * 增强构造函数：
 *          ≤C> RedisConnection(RedisClient redisClient, Channel channel, CompletableFuture≤C> connectionPromise)
 *          RedisConnection(RedisClient redisClient)
 *      拦截器：org.apache.skywalking.apm.plugin.redisson.v3.RedisConnectionMethodInterceptor
 * 增强方法：
 *          ChannelFuture send(CommandsData data)
 *          ≤T, R> ChannelFuture send(CommandData≤T, R> data)
 *      拦截器：org.apache.skywalking.apm.plugin.redisson.v3.RedisConnectionMethodInterceptor
 * </pre>
 */
public class RedisConnectionInstrumentation extends ClassInstanceMethodsEnhancePluginDefineV2 {

    private static final String ENHANCE_CLASS = "org.redisson.client.RedisConnection";

    private static final String REDISSON_METHOD_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.redisson.v3.RedisConnectionMethodInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, "org.redisson.client.RedisClient");
                }

                @Override
                public String getConstructorInterceptor() {
                    return REDISSON_METHOD_INTERCEPTOR_CLASS;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptV2Point[] getInstanceMethodsInterceptV2Points() {
        return new InstanceMethodsInterceptV2Point[] {
            new InstanceMethodsInterceptV2Point() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("send");
                }

                @Override
                public String getMethodsInterceptorV2() {
                    return REDISSON_METHOD_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    public ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }
}
