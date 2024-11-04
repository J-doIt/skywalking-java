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

package org.apache.skywalking.apm.plugin.asf.dubbo3;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.WitnessMethod;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * <pre>
 * 增强类：org.apache.dubbo.monitor.support.MonitorFilter
 * 增强方法：Result invoke(Invoker≤?> invoker, Invocation invocation)
 *      拦截器：org.apache.skywalking.apm.plugin.asf.dubbo3.DubboInterceptor
 * </pre>
 */
public class DubboInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String ENHANCE_CLASS = "org.apache.dubbo.monitor.support.MonitorFilter";

    public static final String INTERCEPT_POINT_METHOD = "invoke";

    public static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.asf.dubbo3.DubboInterceptor";

    public static final String CONTEXT_TYPE_NAME = "org.apache.dubbo.rpc.RpcContext";

    public static final String GET_SERVER_CONTEXT_METHOD_NAME = "getServerContext";

    public static final String CONTEXT_ATTACHMENT_TYPE_NAME = "org.apache.dubbo.rpc.RpcContextAttachment";

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        // 不增强构造函数
        return null;
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(INTERCEPT_POINT_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    // 不重新方法的参数
                    return false;
                }
            }
        };
    }

    @Override
    protected List<WitnessMethod> witnessMethods() {
        // 见证方法：RpcContext.getServerContext()，且返回值类型是 RpcContextAttachment
        return Collections.singletonList(
            new WitnessMethod(
                CONTEXT_TYPE_NAME,
                named(GET_SERVER_CONTEXT_METHOD_NAME).and(
                    returns(named(CONTEXT_ATTACHMENT_TYPE_NAME)))
            ));
    }

}
