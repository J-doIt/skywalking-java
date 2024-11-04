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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcContextAttachment;
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

import java.lang.reflect.Method;

/**
 * {@link DubboInterceptor} define how to enhance class {@link org.apache.dubbo.monitor.support.MonitorFilter#invoke(Invoker,
 * Invocation)}. the trace context transport to the provider side by {@link RpcContext#getServerAttachment}.but all the
 * version of dubbo framework below 3.0.0 don't support {@link RpcContext#getClientAttachment}, we support another way
 * to support it.
 *
 * <pre>
 * (DubboInterceptor 定义了如何增强类 MonitorFilter.invoke（Invoker， Invocation）。
 * 通过 RpcContext.getServerAttachment() 将 跟踪上下文 传输 到 provider端。
 * 但是 所有低于 3.0.0 的 dubbo 框架版本都不支持 RpcContext.getClientAttachment()，我们支持另一种方式来支持它。)
 *
 * 增强类：org.apache.dubbo.monitor.support.MonitorFilter
 * 增强方法：Result invoke(Invoker≤?> invoker, Invocation invocation)
 * </pre>
 */
public class DubboInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String ARGUMENTS = "arguments";

    /**
     * Consumer: The serialized trace context data will
     * inject to the {@link RpcContext#getClientAttachment} for transport to provider side.
     * <p>
     * Provider: The serialized trace context data will extract from
     * {@link RpcContext#getServerAttachment}. current trace segment will ref if the serialization context data is not
     * null.
     *
     * <pre>
     * (消费者：序列化的跟踪上下文数据 将注入 RpcContext.getClientAttachment 用于传输到 提供者。
     * 提供者：序列化的跟踪上下文数据 将从 RpcContext.getServerAttachment 中提取。如果序列化上下文数据不为空，则当前跟踪段将被引用。)
     * </pre>
     *
     * @param objInst 被增强了的 MonitorFilter
     * @param method invoke
     * @param allArguments [invoker, invocation]
     * @param argumentsTypes [Invoker≤?>, Invocation]
     * @param result Result
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Invoker invoker = (Invoker) allArguments[0];
        Invocation invocation = (Invocation) allArguments[1];

        // 通过 Invocation.Invoker.url 判断当前的调用是否发生于客户端
        boolean isConsumer = isConsumer(invocation);

        // 获取 当前的 RpcContextAttachment
        RpcContextAttachment attachment = isConsumer ? RpcContext.getClientAttachment() : RpcContext.getServerAttachment();
        URL requestURL = invoker.getUrl();

        AbstractSpan span;

        final String host = requestURL.getHost();
        final int port = requestURL.getPort();

        // 标志：需要收集客户端的远程调用参数
        boolean needCollectArguments;
        // 参数长度阈值
        int argumentsLengthThreshold;
        // 消费者
        if (isConsumer) {
            // 创建 跨进程传输载体
            final ContextCarrier contextCarrier = new ContextCarrier();
            // 创建 exit span，并将 当前的 TracerContext 和 该 exit span 的相关信息 注入 到 ContextCarrier 中
            span = ContextManager.createExitSpan(
                generateOperationName(requestURL, invocation), contextCarrier, host + ":" + port);
            //invocation.getAttachments().put("contextData", contextDataStr);
            //@see https://github.com/alibaba/dubbo/blob/dubbo-2.5.3/dubbo-rpc/dubbo-rpc-api/src/main/java/com/alibaba/dubbo/rpc/RpcInvocation.java#L154-L161
            CarrierItem next = contextCarrier.items();
            // 将 上游消费者 的 链路信息（下文载体的 key-value） 放入到 attachment 中传递给 下游的 生产者
            while (next.hasNext()) {
                next = next.next();
                attachment.setAttachment(next.getHeadKey(), next.getHeadValue());
                if (invocation.getAttachments().containsKey(next.getHeadKey())) {
                    invocation.getAttachments().remove(next.getHeadKey());
                }
            }
            needCollectArguments = DubboPluginConfig.Plugin.Dubbo.COLLECT_CONSUMER_ARGUMENTS;
            argumentsLengthThreshold = DubboPluginConfig.Plugin.Dubbo.CONSUMER_ARGUMENTS_LENGTH_THRESHOLD;
        } else {
            // 服务提供者

            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            // 将 attachment 中的 链路信息 放到 本链路（下文载体的 key-value） 中
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(attachment.getAttachment(next.getHeadKey()));
            }

            // 创建 entry span ，并从 上下文载体 中 提取 追踪信息
            span = ContextManager.createEntrySpan(generateOperationName(requestURL, invocation), contextCarrier);
            span.setPeer(attachment.getRemoteAddressString());
            needCollectArguments = DubboPluginConfig.Plugin.Dubbo.COLLECT_PROVIDER_ARGUMENTS;
            argumentsLengthThreshold = DubboPluginConfig.Plugin.Dubbo.PROVIDER_ARGUMENTS_LENGTH_THRESHOLD;
        }

        // 设置 Span 的 URL 标签
        Tags.URL.set(span, generateRequestURL(requestURL, invocation));
        // 从 invocation 中收集 参数 到 span 的 arguments 标签中
        collectArguments(needCollectArguments, argumentsLengthThreshold, span, invocation);
        // 将 span 定义为 Dubbo 类型的组件
        span.setComponent(ComponentsDefine.DUBBO);
        // 将 span 的 Layer 设置为 RPC 框架
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Result result = (Result) ret;

        if (result != null && result.getException() != null) {
            // 如果返回值是异常类，执行异常处理
            dealException(result.getException());
        }

        // 停止当前 Span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        dealException(t);
    }

    /**
     * Log the throwable, which occurs in Dubbo RPC service.
     */
    private void dealException(Throwable throwable) {
        // 获取当前活跃的 Span
        AbstractSpan span = ContextManager.activeSpan();
        // 设置 active span 的 log 为 Throwable
        span.log(throwable);
    }

    /**
     * To judge if current is in provider side.
     * 通过 Invocation.Invoker.url 判断当前的调用是否发生于客户端
     */
    private static boolean isConsumer(Invocation invocation) {
        Invoker<?> invoker = invocation.getInvoker();
        // As RpcServiceContext may not been reset when it's role switched from provider
        // to consumer in the same thread, but RpcInvocation is always correctly bounded
        // to the current request or serve request, https://github.com/apache/skywalking-java/pull/110
        return invoker.getUrl()
                .getParameter("side", "provider")
                .equals("consumer");
    }

    /**
     * Format operation name. e.g. org.apache.skywalking.apm.plugin.test.Test.test(String)
     *
     * @return operation name.
     */
    private String generateOperationName(URL requestURL, Invocation invocation) {
        StringBuilder operationName = new StringBuilder();
        String groupStr = requestURL.getParameter(CommonConstants.GROUP_KEY);
        groupStr = StringUtil.isEmpty(groupStr) ? "" : groupStr + "/";
        operationName.append(groupStr);
        operationName.append(requestURL.getPath());
        operationName.append("." + invocation.getMethodName() + "(");
        for (Class<?> classes : invocation.getParameterTypes()) {
            operationName.append(classes.getSimpleName() + ",");
        }

        if (invocation.getParameterTypes().length > 0) {
            operationName.delete(operationName.length() - 1, operationName.length());
        }

        operationName.append(")");

        return operationName.toString();
    }

    /**
     * Format request url. e.g. dubbo://127.0.0.1:20880/org.apache.skywalking.apm.plugin.test.Test.test(String).
     *
     * @return request url.
     */
    private String generateRequestURL(URL url, Invocation invocation) {
        StringBuilder requestURL = new StringBuilder();
        requestURL.append(url.getProtocol() + "://");
        requestURL.append(url.getHost());
        requestURL.append(":" + url.getPort() + "/");
        requestURL.append(generateOperationName(url, invocation));
        return requestURL.toString();
    }

    private void collectArguments(boolean needCollectArguments,
                                  int argumentsLengthThreshold,
                                  AbstractSpan span,
                                  Invocation invocation) {
        if (needCollectArguments && argumentsLengthThreshold > 0) {
            // 从 Invocation 获取参数
            Object[] parameters = invocation.getArguments();
            if (parameters != null && parameters.length > 0) {
                StringBuilder stringBuilder = new StringBuilder();
                boolean first = true;
                for (Object parameter : parameters) {
                    if (!first) {
                        stringBuilder.append(",");
                    }
                    stringBuilder.append(parameter);
                    if (stringBuilder.length() > argumentsLengthThreshold) {
                        stringBuilder.append("...");
                        break;
                    }
                    first = false;
                }
                // 将 参数字符串 加入到 span 的 arguments 标签中
                span.tag(ARGUMENTS, stringBuilder.toString());
            }
        }
    }
}
