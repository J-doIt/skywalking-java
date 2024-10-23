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

package org.apache.skywalking.apm.plugin.tomcat10x;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Request;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.tomcat.util.http.Parameters;

import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class TomcatInvokeInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String SERVLET_RESPONSE_CLASS = "jakarta.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    /**
     * @param objInst 被增强了的 StandardHostValve 类
     * @param method StandardHostValve 的 invoke 方法（{@code invoke(Request request, Response response)}）
     * @param allArguments
     * @param argumentsTypes
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Request request = (Request) allArguments[0];
        // 创建 跨进程Trace上下文载体
        ContextCarrier contextCarrier = new ContextCarrier();

        // 遍历上下文载体中的所有项（sw8、sw8-x、sw8-correlation），并从请求头中获取对应的值
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            // 从请求头中获取 sw8、sw8-x、sw8-correlation 对应的值，并设置到 新创建的 contextCarrier 的 item 中
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }
        // 构建操作名称，格式为 "HTTP方法:请求URI"
        String operationName =  String.join(":", request.getMethod(), request.getRequestURI());
        // 创建入口 Span 并设置上下文载体
        AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
        // 设置 Span 的 URL 标签
        Tags.URL.set(span, request.getRequestURL().toString());
        // 设置 Span 的 HTTP 方法标签
        Tags.HTTP.METHOD.set(span, request.getMethod());
        // 设置 Span 的组件信息为 Tomcat
        span.setComponent(ComponentsDefine.TOMCAT);
        // 设置 Span 的层级为 HTTP
        SpanLayer.asHttp(span);

        // 如果配置了收集 HTTP 参数，则调用 collectHttpParam 方法收集参数
        if (TomcatPluginConfig.Plugin.Tomcat.COLLECT_HTTP_PARAMS) {
            // 收集请求中的参数
            collectHttpParam(request, span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Request request = (Request) allArguments[0];
        HttpServletResponse response = (HttpServletResponse) allArguments[1];

        // 获取当前活跃的 Span
        AbstractSpan span = ContextManager.activeSpan();
        // 设置响应状态码标签
        Tags.HTTP_RESPONSE_STATUS_CODE.set(span, response.getStatus());
        // 如果响应状态码大于等于 400，则标记 Span 为错误
        if (response.getStatus() >= 400) {
            span.errorOccurred();
        }
        // Active HTTP parameter collection automatically in the profiling context.
        // （在 Profiling 上下文中自动收集 HTTP 参数）
        if (!TomcatPluginConfig.Plugin.Tomcat.COLLECT_HTTP_PARAMS && span.isProfiling()) {
            // 收集请求中的参数
            collectHttpParam(request, span);
        }
        // 从运行时上下文中移除转发请求标志
        ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        // 停止当前 Span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    /**
     * 收集 HTTP 参数 到 span
     */
    private void collectHttpParam(Request request, AbstractSpan span) {
        final Map<String, String[]> parameterMap = new HashMap<>();
        final org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
        // 从 Coyote 请求对象中获取参数
        final Parameters parameters = coyoteRequest.getParameters();
        // 遍历所有参数名，并将参数名和对应的值存入 parameterMap
        for (final Enumeration<String> names = parameters.getParameterNames(); names.hasMoreElements(); ) {
            final String name = names.nextElement();
            parameterMap.put(name, parameters.getParameterValues(name));
        }

        // 如果 parameterMap 不为空，则将其转换为字符串并设置为 Span 的 HTTP 参数标签
        if (!parameterMap.isEmpty()) {
            // 将参数映射转换为字符串
            String tagValue = CollectionUtil.toString(parameterMap);
            // 如果配置了 HTTP 参数长度阈值，则截断字符串
            tagValue = TomcatPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                StringUtil.cut(tagValue, TomcatPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) :
                tagValue;
            // 设置 Span 的 HTTP 参数标签
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }
}
