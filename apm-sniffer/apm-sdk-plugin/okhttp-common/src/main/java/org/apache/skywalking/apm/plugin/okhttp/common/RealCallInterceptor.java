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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link RealCallInterceptor} intercept the synchronous http calls by the discovery of okhttp.
 * <pre>
 * (RealCallInterceptor 通过发现 okhttp 截获 同步http调用 。)
 * </pre>
 */
public class RealCallInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

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
     * 增强构造函数 RealCall ( OkHttpClient, Request, forWebSocket:Boolean )
     * @param objInst RealCall
     * @param allArguments  OkHttpClient, Request, forWebSocket:Boolean
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        // 将 Request 对象复制给 动态增强的字段
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }

    /**
     * Get the {@link Request} from {@link EnhancedInstance}, then create {@link AbstractSpan} and set host,
     * port, kind, component, url from {@link Request}. Through the reflection of the way, set the http header
     * of context data into {@link Request#headers()}.
     *
     * @param objInst RealCall
     * @param method fun execute(): Response
     * @param result change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        Request request = (Request) objInst.getSkyWalkingDynamicField();

        // 创建 跨进程传输载体
        ContextCarrier contextCarrier = new ContextCarrier();
        HttpUrl requestUrl = request.url();
        // 创建 exit span，并将 当前的 TracerContext 和 该 exit span 的相关信息 注入 到 ContextCarrier 中
        AbstractSpan span = ContextManager.createExitSpan(requestUrl.uri().getPath(), contextCarrier,
                requestUrl.host() + ":" + requestUrl.port());
        span.setComponent(ComponentsDefine.OKHTTP);
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, requestUrl.uri().toString());
        SpanLayer.asHttp(span);

        // 如果 headers 不为空
        if (FIELD_HEADERS_OF_REQUEST != null) {
            // 重新 构建 header
            Headers.Builder headerBuilder = request.headers().newBuilder();
            CarrierItem next = contextCarrier.items();
            // 并将 下文载体的 key-value 设置到 待构建的 header
            while (next.hasNext()) {
                next = next.next();
                headerBuilder.set(next.getHeadKey(), next.getHeadValue());
            }
            // 将 request 对象的 headers 字段设置为 新构建的 header
            FIELD_HEADERS_OF_REQUEST.set(request, headerBuilder.build());
        }
    }

    /**
     * Get the status code from {@link Response}, when status code greater than 400, it means there was some errors in
     * the server. Finish the {@link AbstractSpan}.
     *
     * @param objInst RealCall
     * @param method fun execute(): Response
     * @param allArguments 空参
     * @param argumentsTypes 空参
     * @param ret the method's original return value.
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        Response response = (Response) ret;
        if (response != null) {
            int statusCode = response.code();
            AbstractSpan span = ContextManager.activeSpan();
            // 设置 response 的 状态码到 span 的 tag
            Tags.HTTP_RESPONSE_STATUS_CODE.set(span, statusCode);
            if (statusCode >= 400) {
                span.errorOccurred();
            }
        }

        // 停止 当前活跃的 Span。
        ContextManager.stopSpan();

        return ret;
    }

    /**
     * execute() 执行失败 的 异常处理
     *
     * @param objInst RealCall
     * @param method fun execute(): Response
     * @param allArguments 空参
     * @param argumentsTypes 空参
     * @param t the exception occur.
     */
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan abstractSpan = ContextManager.activeSpan();
        // 设置 active span 的 log 为 Throwable
        abstractSpan.log(t);
    }
}
