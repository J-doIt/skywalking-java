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

package org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.RequestUtil;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.SpringMVCPluginConfig;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.exception.IllegalMethodStackDepthException;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.exception.ServletResponseNotFoundException;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.CONTROLLER_METHOD_STACK_DEPTH;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.FORWARD_REQUEST_FLAG;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

/**
 * the abstract method interceptor
 */
public abstract class AbstractMethodInterceptor implements InstanceMethodsAroundInterceptor {
    private static final ILog LOGGER = LogManager.getLogger(AbstractMethodInterceptor.class);
    /** 标记 javax.servlet.http.HttpServletResponse 类中是否存在 getStatus 方法 */
    private static boolean IS_SERVLET_GET_STATUS_METHOD_EXIST;
    /** 标记 jakarta.servlet.http.HttpServletResponse 类中是否存在 getStatus 方法 */
    private static boolean IS_JAKARTA_SERVLET_GET_STATUS_METHOD_EXIST;
    private static final String SERVLET_RESPONSE_CLASS = "javax.servlet.http.HttpServletResponse";
    private static final String JAKARTA_SERVLET_RESPONSE_CLASS = "jakarta.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    /** 标记当前是否运行在 Servlet容器 中 */
    private static boolean IN_SERVLET_CONTAINER;
    /** 标记是否使用了 javax.* 包下的 Servlet 相关 API */
    private static boolean IS_JAVAX = false;
    /** 标记是否使用了 jakarta.* 包下的 Servlet 相关 API */
    private static boolean IS_JAKARTA = false;

    /**
     * 静态初始化块，用于检测Servlet环境及相关API的存在性
     */
    static {
        // 检查 javax.servlet.http.HttpServletResponse 中是否存在 getStatus 方法
        IS_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(
                AbstractMethodInterceptor.class.getClassLoader(), SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
        // 检查 jakarta.servlet.http.HttpServletResponse 中是否存在 getStatus 方法
        IS_JAKARTA_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(
                AbstractMethodInterceptor.class.getClassLoader(), JAKARTA_SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
        // 尝试加载 javax.servlet.http.HttpServletResponse，判断是否在Servlet容器中，并标记使用了 javax.*
        try {
            Class.forName(SERVLET_RESPONSE_CLASS, true, AbstractMethodInterceptor.class.getClassLoader());
            IN_SERVLET_CONTAINER = true;
            IS_JAVAX = true;
        } catch (Exception ignore) {
            // 如果 javax.* 不可用，尝试加载 jakarta.servlet.http.HttpServletResponse，判断是否在Servlet容器中，并标记使用了 jakarta.*
            try {
                Class.forName(
                    JAKARTA_SERVLET_RESPONSE_CLASS, true, AbstractMethodInterceptor.class.getClassLoader());
                IN_SERVLET_CONTAINER = true;
                IS_JAKARTA = true;
            } catch (Exception ignore2) {
                // 若两者均不可用，则标记不在Servlet容器中
                IN_SERVLET_CONTAINER = false;
            }
        }
    }

    public abstract String getRequestURL(Method method);

    /**
     * @param objInst Spring的增强类的定义 中 定要的被增强的类的 实例
     * @param method Spring的增强类的定义 中 定要的被拦截的 方法
     * @param allArguments
     * @param argumentsTypes
     * @param result 如果要截断方法，请更改此结果。
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        // 从 RuntimeContext 获取 SW_FORWARD_REQUEST_FLAG 标识
        Boolean forwardRequestFlag = (Boolean) ContextManager.getRuntimeContext().get(FORWARD_REQUEST_FLAG);
        /**
         * Spring MVC plugin do nothing if current request is forward request.
         * Ref: https://github.com/apache/skywalking/pull/1325
         * (如果当前请求是 forward request，则 Spring MVC 插件不执行任何操作。)
         */
        if (forwardRequestFlag != null && forwardRequestFlag) {
            return;
        }

        // 从 RuntimeContext 获取 SW_REQUEST（请求）
        Object request = ContextManager.getRuntimeContext().get(REQUEST_KEY_IN_RUNTIME_CONTEXT);

        if (request != null) {
            // 从 RuntimeContext 获取 SW_CONTROLLER_METHOD_STACK_DEPTH（SW 控制器方法堆栈深度？）
            StackDepth stackDepth = (StackDepth) ContextManager.getRuntimeContext().get(CONTROLLER_METHOD_STACK_DEPTH);

            // 如果当前 控制器方法堆栈深度 为空
            if (stackDepth == null) {
                // 创建 上下文载体
                final ContextCarrier contextCarrier = new ContextCarrier();

                // 判断
                //      是运行在 Servlet容器 中
                //   && 用了 javax.* 包下的 Servlet 相关 API
                //   && 请求对象是 javax.servlet.http.HttpServletRequest 的实例
                if (IN_SERVLET_CONTAINER && IS_JAVAX && HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                    // 将 请求对象 request 转换为 HttpServletRequest 对象
                    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                    // 从 HttpServletRequest 中提取头部信息，并设置到 ContextCarrier 中
                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        next.setHeadValue(httpServletRequest.getHeader(next.getHeadKey()));
                    }

                    // 构建操作名称
                    String operationName = this.buildOperationName(method, httpServletRequest.getMethod(),
                            (EnhanceRequireObjectCache) objInst.getSkyWalkingDynamicField());
                    // 创建入口 Span 并设置相关标签
                    AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
                    // 设置 span 的 URL 标签
                    Tags.URL.set(span, httpServletRequest.getRequestURL().toString());
                    // 设置 span 的 HTTP 方法标签
                    Tags.HTTP.METHOD.set(span, httpServletRequest.getMethod());
                    // 设置 span 的 组件为 SpringMVC
                    span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
                    // 设置 span 层为 HTTP
                    SpanLayer.asHttp(span);

                    // 如果配置中启用了收集 HTTP 参数
                    if (SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS) {
                        // 收集 HTTP 参数
                        RequestUtil.collectHttpParam(httpServletRequest, span);
                    }

                    // 如果配置中指定了需要收集的 HTTP 头部
                    if (!CollectionUtil.isEmpty(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS)) {
                        // 收集指定的 HTTP 头部
                        RequestUtil.collectHttpHeaders(httpServletRequest, span);
                    }
                }
                // 判断
                //      是运行在 Servlet容器 中
                //   && 使用了 jakarta.* 包下的 Servlet 相关 API
                //   && 请求对象是 jakarta.servlet.http.HttpServletRequest 的实例
                else if (IN_SERVLET_CONTAINER && IS_JAKARTA && jakarta.servlet.http.HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                    final jakarta.servlet.http.HttpServletRequest httpServletRequest = (jakarta.servlet.http.HttpServletRequest) request;
                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        next.setHeadValue(httpServletRequest.getHeader(next.getHeadKey()));
                    }

                    String operationName =
                            this.buildOperationName(method, httpServletRequest.getMethod(),
                                    (EnhanceRequireObjectCache) objInst.getSkyWalkingDynamicField());
                    AbstractSpan span =
                            ContextManager.createEntrySpan(operationName, contextCarrier);
                    Tags.URL.set(span, httpServletRequest.getRequestURL().toString());
                    Tags.HTTP.METHOD.set(span, httpServletRequest.getMethod());
                    span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
                    SpanLayer.asHttp(span);

                    if (SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS) {
                        RequestUtil.collectHttpParam(httpServletRequest, span);
                    }

                    if (!CollectionUtil
                            .isEmpty(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS)) {
                        RequestUtil.collectHttpHeaders(httpServletRequest, span);
                    }
                }
                // 判断：请求对象是 org.springframework.http.server.reactive.ServerHttpRequest 的实例
                else if (ServerHttpRequest.class.isAssignableFrom(request.getClass())) {
                    final ServerHttpRequest serverHttpRequest = (ServerHttpRequest) request;
                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        next.setHeadValue(serverHttpRequest.getHeaders().getFirst(next.getHeadKey()));
                    }

                    String operationName = this.buildOperationName(method, serverHttpRequest.getMethod().name(),
                            (EnhanceRequireObjectCache) objInst.getSkyWalkingDynamicField());
                    AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
                    Tags.URL.set(span, serverHttpRequest.getURI().toString());
                    Tags.HTTP.METHOD.set(span, serverHttpRequest.getMethod().name());
                    span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
                    SpanLayer.asHttp(span);

                    if (SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS) {
                        RequestUtil.collectHttpParam(serverHttpRequest, span);
                    }

                    if (!CollectionUtil.isEmpty(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS)) {
                        RequestUtil.collectHttpHeaders(serverHttpRequest, span);
                    }
                } else {
                    throw new IllegalStateException("this line should not be reached");
                }

                // 创建 控制器方法堆栈深度，并设置到 RuntimeContext
                stackDepth = new StackDepth();
                ContextManager.getRuntimeContext().put(CONTROLLER_METHOD_STACK_DEPTH, stackDepth);
            } else {
                // 如果当前 控制器方法堆栈深度 不为空，则创建 Local Span（普通的方法调用）
                AbstractSpan span = ContextManager.createLocalSpan(buildOperationName(objInst, method));
                // 设置 span 的 组件为 SpringMVC
                span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
            }

            // 控制器方法堆栈深度++（）
            stackDepth.increment();
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        final RuntimeContext runtimeContext = ContextManager.getRuntimeContext();
        // 从 RuntimeContext 获取 SW_FORWARD_REQUEST_FLAG 标识
        Boolean forwardRequestFlag = (Boolean) runtimeContext.get(FORWARD_REQUEST_FLAG);
        /**
         * Spring MVC plugin do nothing if current request is forward request.
         * Ref: https://github.com/apache/skywalking/pull/1325
         * （如果当前请求是 forward request，则 Spring MVC 插件不执行任何操作。）
         */
        if (forwardRequestFlag != null && forwardRequestFlag) {
            return ret;
        }

        // 从 RuntimeContext 获取 SW_REQUEST（请求）
        Object request = runtimeContext.get(REQUEST_KEY_IN_RUNTIME_CONTEXT);

        if (request != null) {
            try {
                // 从 RuntimeContext 获取 SW_CONTROLLER_METHOD_STACK_DEPTH（SW 控制器方法堆栈深度？）
                StackDepth stackDepth = (StackDepth) runtimeContext.get(CONTROLLER_METHOD_STACK_DEPTH);
                if (stackDepth == null) {
                    // 此时 控制器方法堆栈深度 不应该为空
                    throw new IllegalMethodStackDepthException();
                } else {
                    // 控制器方法堆栈深度--
                    stackDepth.decrement();
                }

                // 获取当前活跃的 Span
                AbstractSpan span = ContextManager.activeSpan();

                // 若此时 stackDepth 为 0
                if (stackDepth.depth() == 0) {
                    // 从 RuntimeContext 获取 SW_RESPONSE（响应）
                    Object response = runtimeContext.get(RESPONSE_KEY_IN_RUNTIME_CONTEXT);
                    if (response == null) {
                        throw new ServletResponseNotFoundException();
                    }

                    Integer statusCode = null;

                    // 获取应答状态码
                    if (IS_SERVLET_GET_STATUS_METHOD_EXIST && HttpServletResponse.class.isAssignableFrom(response.getClass())) {
                        statusCode = ((HttpServletResponse) response).getStatus();
                    } else if (IS_JAKARTA_SERVLET_GET_STATUS_METHOD_EXIST && jakarta.servlet.http.HttpServletResponse.class.isAssignableFrom(response.getClass())) {
                        statusCode = ((jakarta.servlet.http.HttpServletResponse) response).getStatus();
                    } else if (ServerHttpResponse.class.isAssignableFrom(response.getClass())) {
                        if (IS_SERVLET_GET_STATUS_METHOD_EXIST || IS_JAKARTA_SERVLET_GET_STATUS_METHOD_EXIST) {
                            statusCode = ((ServerHttpResponse) response).getRawStatusCode();
                        }
                        // 从 runtimeContext 获取 SW_REACTIVE_RESPONSE_ASYNC_SPAN（响应式的 AbstractSpan ，在 InvokeInterceptor.beforeMethod() 中被 put 了）
                        Object context = runtimeContext.get(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT);
                        if (context != null) {
                            // 响应式的span 在 当前跟踪上下文（tracing context） 结束（但该span 仍然是活动的，直到 asyncFinish 调用（InvokeInterceptor.afterMethod()））
                            ((AbstractSpan[]) context)[0] = span.prepareForAsync();
                        }
                    }

                    if (statusCode != null) {
                        // 设置 span 的 应答状态码 的 tag
                        Tags.HTTP_RESPONSE_STATUS_CODE.set(span, statusCode);
                        if (statusCode >= 400) {
                            span.errorOccurred();
                        }
                    }

                    // 移除 runtimeContext 中的 这些值
                    runtimeContext.remove(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT);
                    runtimeContext.remove(REQUEST_KEY_IN_RUNTIME_CONTEXT);
                    runtimeContext.remove(RESPONSE_KEY_IN_RUNTIME_CONTEXT);
                    runtimeContext.remove(CONTROLLER_METHOD_STACK_DEPTH);
                }

                // Active HTTP parameter collection automatically in the profiling context.
                // （在 性能分析上下文 中自动主动收集 HTTP 参数。）
                if (!SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS && span.isProfiling()) {
                    if (IS_JAVAX && HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                        RequestUtil.collectHttpParam((HttpServletRequest) request, span);
                    } else if (IS_JAKARTA && jakarta.servlet.http.HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                        RequestUtil.collectHttpParam((jakarta.servlet.http.HttpServletRequest) request, span);
                    } else if (ServerHttpRequest.class.isAssignableFrom(request.getClass())) {
                        RequestUtil.collectHttpParam((ServerHttpRequest) request, span);
                    }
                }
            } finally {
                // 结束 span
                ContextManager.stopSpan();
            }
        }

        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private String buildOperationName(Object invoker, Method method) {
        StringBuilder operationName = new StringBuilder(invoker.getClass().getName()).append(".")
                                                                                     .append(method.getName())
                                                                                     .append("(");
        for (Class<?> type : method.getParameterTypes()) {
            operationName.append(type.getName()).append(",");
        }

        if (method.getParameterTypes().length > 0) {
            operationName = operationName.deleteCharAt(operationName.length() - 1);
        }

        return operationName.append(")").toString();
    }

    /**
     * @param method
     * @param httpMethod
     * @param pathMappingCache 在 ControllerConstructorInterceptor.onConstruct() 中被加入到动态字段的
     * @return
     */
    private String buildOperationName(Method method, String httpMethod, EnhanceRequireObjectCache pathMappingCache) {
        String operationName;
        if (SpringMVCPluginConfig.Plugin.SpringMVC.USE_QUALIFIED_NAME_AS_ENDPOINT_NAME) {
            operationName = MethodUtil.generateOperationName(method);
        } else {
            String requestURL = pathMappingCache.findPathMapping(method);
            if (requestURL == null) {
                // 使用 RequestMapping 注解的第一个mapping值 作为 requestURL
                requestURL = getRequestURL(method);
                pathMappingCache.addPathMapping(method, requestURL);
                requestURL = pathMappingCache.findPathMapping(method);
            }
            operationName =  String.join(":", httpMethod, requestURL);
        }

        return operationName;
    }
}
