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

package org.apache.skywalking.apm.plugin.okhttp.common;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import okhttp3.Response;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * <pre>
 * 增强类 RealCall
 * 拦截方法：fun getResponseWithInterceptorChain...(): Response，
 * </pre>
 */
public class CallInterceptor implements InstanceMethodsAroundInterceptor {

    private static Field FIELD_HEADERS_OF_REQUEST;

    static {
        try {
            final Field field = Request.class.getDeclaredField("headers");
            field.setAccessible(true);
            FIELD_HEADERS_OF_REQUEST = field;
        } catch (Exception ignore) {
            FIELD_HEADERS_OF_REQUEST = null;
        }
    }

    /**
     *
     * @param objInst 增强后的 RealCall
     * @param method fun getResponseWithInterceptorChain(): Response {}
     * @param allArguments 空参
     * @param argumentsTypes 空参
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        // objInst 的 动态增强的字段 已在 RealCallInterceptor.onConstruct() 中被 set 了值。
        Request request = (Request) objInst.getSkyWalkingDynamicField();
        HttpUrl requestUrl = request.url();
        // 创建 操作名为 uri 的 exit span（没有 inject ContextCarrier ）
        AbstractSpan span = ContextManager.createExitSpan(requestUrl.uri()
                .getPath(), requestUrl.host() + ":" + requestUrl.port());
        ContextCarrier contextCarrier = new ContextCarrier();
        // 在此处 inject ContextCarrier
        ContextManager.inject(contextCarrier);
        // 设置 span 的 组件 为 OKHttp
        span.setComponent(ComponentsDefine.OKHTTP);
        // 设置 span 的 标记（http.method） 为 OKHttp
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, requestUrl.uri().toString());
        SpanLayer.asHttp(span);
        if (FIELD_HEADERS_OF_REQUEST != null) {
            // 重新构建 http headers
            Headers.Builder headerBuilder = request.headers().newBuilder();
            // 将 contextCarrier 的 item 设置到 待构建 的 header 中
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                headerBuilder.set(next.getHeadKey(), next.getHeadValue());
            }
            // 为 header 字段重新赋值
            FIELD_HEADERS_OF_REQUEST.set(request, headerBuilder.build());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        Response response = (Response) ret;
        if (response != null) {
            int statusCode = response.code();
            // 取出 active 的 span
            AbstractSpan span = ContextManager.activeSpan();
            // 为 该 span 设置 http.status_code
            Tags.HTTP_RESPONSE_STATUS_CODE.set(span, statusCode);
            if (statusCode >= 400) {
                span.errorOccurred();
            }
        }
        // stop span，并将该 span 归档到 所属 traceSegment 中
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 Throwable
        ContextManager.activeSpan().log(t);
    }
}
