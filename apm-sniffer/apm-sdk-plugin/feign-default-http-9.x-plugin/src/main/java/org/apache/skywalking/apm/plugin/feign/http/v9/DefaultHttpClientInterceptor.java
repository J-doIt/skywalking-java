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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.skywalking.apm.util.StringUtil;

import static feign.Util.valuesOrEmpty;

/**
 * {@link DefaultHttpClientInterceptor} intercept the default implementation of http calls by the Feign.
 *
 * <pre>
 * (DefaultHttpClientInterceptor 拦截 Feign 的 http 调用 的 默认实现。)
 *
 * 增强类 feign.Client$Default
 * 增强方法：Response execute(Request request, Request.Options options)
 * </pre>
 */
public class DefaultHttpClientInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    /** feign.Request 的 headers 字段 */
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
     * Get the {@link feign.Request} from {@link EnhancedInstance}, then create {@link AbstractSpan} and set host, port,
     * kind, component, url from {@link feign.Request}. Through the reflection of the way, set the http header of
     * context data into {@link feign.Request#headers}.
     *
     * @param objInst feign.Client$Default 的增强类 的实例
     * @param method Response execute(Request request, Request.Options options)
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable NoSuchFieldException or IllegalArgumentException
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Request request = (Request) allArguments[0];
        URL url = new URL(request.url());
        // 创建 跨进程传输载体
        ContextCarrier contextCarrier = new ContextCarrier();
        int port = url.getPort() == -1 ? 80 : url.getPort();
        String remotePeer = url.getHost() + ":" + port;
        String operationName = url.getPath();
        FeignResolvedURL feignResolvedURL = PathVarInterceptor.URL_CONTEXT.get();
        if (feignResolvedURL != null) {
            try {
                // 将 feign解析前的url 替换为 解析后的 url
                operationName = operationName.replace(feignResolvedURL.getUrl(), feignResolvedURL.getOriginUrl());
            } finally {
                // 移除 PathVarInterceptor 的 当前线程 的 线程变量
                PathVarInterceptor.URL_CONTEXT.remove();
            }
        }
        if (operationName.length() == 0) {
            operationName = "/";
        }
        // 创建 exit span，并将 当前的 TracerContext 和 该 exit span 的相关信息 注入 到 ContextCarrier 中
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, remotePeer);
        // 设置 span 的 组件为 Feign
        span.setComponent(ComponentsDefine.FEIGN);
        // 设置 span 的 HTTP 方法标签
        Tags.HTTP.METHOD.set(span, request.method());
        // 设置 span 的 URL 标签
        Tags.URL.set(span, request.url());
        // 设置 span 层为 HTTP
        SpanLayer.asHttp(span);

        // 否应收集请求的 http 正文
        if (FeignPluginConfig.Plugin.Feign.COLLECT_REQUEST_BODY) {
            boolean needCollectHttpBody = false;
            Iterator<String> stringIterator = valuesOrEmpty(request.headers(), CONTENT_TYPE_HEADER).iterator();
            String contentTypeHeaderValue = stringIterator.hasNext() ? stringIterator.next() : "";
            // 遍历 支持的内容类型前缀
            for (String contentType : FeignPluginConfig.Plugin.Feign.SUPPORTED_CONTENT_TYPES_PREFIX.split(",")) {
                if (contentTypeHeaderValue.startsWith(contentType)) {
                    // 标记为需要收集请求正文
                    needCollectHttpBody = true;
                    break;
                }
            }
            if (needCollectHttpBody) {
                // 收集请求正文
                collectHttpBody(request, span);
            }
        }

        // 如果 feign.Request 的 headers 字段 不为空
        if (FIELD_HEADERS_OF_REQUEST != null) {
            Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
            CarrierItem next = contextCarrier.items();
            // 将 下文载体 的 key-value 放置到 headers
            while (next.hasNext()) {
                next = next.next();
                List<String> contextCollection = new ArrayList<String>(1);
                contextCollection.add(next.getHeadValue());
                headers.put(next.getHeadKey(), contextCollection);
            }
            headers.putAll(request.headers());

            // 为 方法参数 request 的 headers 字段 设置值
            FIELD_HEADERS_OF_REQUEST.set(request, Collections.unmodifiableMap(headers));
        }
    }

    /** 收集请求正文 */
    private void collectHttpBody(final Request request, final AbstractSpan span) {
        if (request.body() == null || request.charset() == null) {
            return;
        }
        String tagValue = new String(request.body(), request.charset());
        // 根据 字符数限制 截取 请求正文
        tagValue = FeignPluginConfig.Plugin.Feign.FILTER_LENGTH_LIMIT > 0 ?
            StringUtil.cut(tagValue, FeignPluginConfig.Plugin.Feign.FILTER_LENGTH_LIMIT) : tagValue;
        // 设置 span 的 HTTP 的 BODY 标签
        Tags.HTTP.BODY.set(span, tagValue);
    }

    /**
     * Get the status code from {@link Response}, when status code greater than 400, it means there was some errors in
     * the server. Finish the {@link AbstractSpan}.
     *
     * @param objInst feign.Client$Default 的增强类 的实例
     * @param method Response execute(Request request, Request.Options options)
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

        // 停止 当前活跃的 Span。
        ContextManager.stopSpan();

        return ret;
    }

    /**  execute() 执行失败 的 异常处理 */
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        // 设置 active span 的 log 为 t
        activeSpan.log(t);
    }
}
