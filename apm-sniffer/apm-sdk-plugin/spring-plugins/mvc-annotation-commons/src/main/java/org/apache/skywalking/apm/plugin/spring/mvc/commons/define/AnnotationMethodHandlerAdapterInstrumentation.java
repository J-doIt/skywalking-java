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

package org.apache.skywalking.apm.plugin.spring.mvc.commons.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * <pre>
 * 增强类 AnnotationMethodHandlerAdapter（后续版本不在使用了）
 * 增强方法：ModelAndView invokeHandlerMethod(HttpServletRequest request, HttpServletResponse response, Object handler)
 *      拦截器：InvokeHandlerMethodInterceptor
 * </pre>
 */
public class AnnotationMethodHandlerAdapterInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        // 不增强构造函数
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 拦截 AnnotationMethodHandlerAdapter 的 invokeHandlerMethod 方法
                    return named("invokeHandlerMethod");
                }

                @Override
                public String getMethodsInterceptor() {
                    return "org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.InvokeHandlerMethodInterceptor";
                }

                @Override
                public boolean isOverrideArgs() {
                    // 不重写增强方法的参数类型
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        // 增强类：AnnotationMethodHandlerAdapter（后续版本不在使用了）
        return byName("org.springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter");
    }
}
