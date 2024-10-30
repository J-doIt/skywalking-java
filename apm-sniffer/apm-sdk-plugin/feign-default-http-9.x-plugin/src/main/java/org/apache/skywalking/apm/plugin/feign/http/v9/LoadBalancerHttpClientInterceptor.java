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

package org.apache.skywalking.apm.plugin.feign.http.v9;

import feign.Request;
import feign.Response;
import java.lang.reflect.Method;
import java.net.URL;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link LoadBalancerHttpClientInterceptor} intercept the implementation of http calls by the Feign LoadBalancer.
 *
 *
 * <pre>
 * (LoadBalancerHttpClientInterceptor 拦截 由 Feign LoadBalancer 执行的 http 调用。)
 *
 * 增强类 LoadBalancerFeignClient（ spring-cloud-openfeign 老版本中有）
 * 增强方法：Response execute(Request request, Request.Options options)
 *      拦截器：AsyncCallInterceptor
 * </pre>
 */
public class LoadBalancerHttpClientInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * @param objInst LoadBalancerFeignClient
     * @param method Response execute(Request request, Request.Options options)
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable NoSuchFieldException or IllegalArgumentException
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Request request = (Request) allArguments[0];
        URL url = new URL(request.url());
        String operationName = url.getPath();
        // 获取 PathVarInterceptor 的 beforeMethod 中得到的 解析的 url
        FeignResolvedURL feignResolvedURL = PathVarInterceptor.URL_CONTEXT.get();
        if (feignResolvedURL != null) {
            try {
                operationName = operationName.replace(feignResolvedURL.getUrl(), feignResolvedURL.getOriginUrl());
            } finally {
                // 移除 PathVarInterceptor 的 当前线程 的 线程变量
                PathVarInterceptor.URL_CONTEXT.remove();
            }
        }
        if (operationName.length() == 0) {
            operationName = "/";
        }
        // 创建 操作名为 Balancer 的 local span
        AbstractSpan span = ContextManager.createLocalSpan("Balancer" + operationName);
        // 设置 span 的 组件为 Feign
        span.setComponent(ComponentsDefine.FEIGN);
        // 设置 span 的 HTTP方法 标签
        Tags.HTTP.METHOD.set(span, request.method());
        // 设置 span 的 URL 标签
        Tags.URL.set(span, request.url());
        // 设置 span 层为 HTTP
        SpanLayer.asHttp(span);
    }

    /**
     * Get the status code from {@link Response}, when status code greater than 400, it means there was some errors in
     * the server. Finish the {@link AbstractSpan}.
     *
     * @param method intercept method
     * @param ret    the method's original return value.
     * @return origin ret
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        Response response = (Response) ret;
        if (response != null) {
            int statusCode = response.status();

            // 获取当前活跃的 Span
            AbstractSpan span = ContextManager.activeSpan();
            // 设置 span 的 response状态码 标签
            Tags.HTTP_RESPONSE_STATUS_CODE.set(span, statusCode);
            // 如果 响应状态码 大于等于 400，则标记 Span 为错误
            if (statusCode >= 400) {
                span.errorOccurred();
            }
        }

        // 停止 当前活跃的 Span
        ContextManager.stopSpan();

        return ret;
    }

    /** execute() 执行失败 的 异常处理 */
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        // 设置 active span 的 log 为 t
        activeSpan.log(t);
        // 标志位 span 的 errorOccurred 标志位为 true
        activeSpan.errorOccurred();
    }
}
