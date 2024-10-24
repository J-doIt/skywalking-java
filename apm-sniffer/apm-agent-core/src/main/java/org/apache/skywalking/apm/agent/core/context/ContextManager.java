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

package org.apache.skywalking.apm.agent.core.context;

import java.util.Objects;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.Agent.OPERATION_NAME_THRESHOLD;

/**
 * {@link ContextManager} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context. <p> What is 'ChildOf'?
 * https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans
 *
 * <p> Also, {@link ContextManager} delegates to all {@link AbstractTracerContext}'s major methods.
 *
 * <pre>
 * (ContextManager 控制 TraceSegment 的整个上下文。
 * 任何 TraceSegment 都与 单线程 相关，因此此上下文使用 ThreadLocal 来维护上下文，
 *      并确保在 TraceSegment start 后，所有ChildOf span 都在相同的上下文中。
 *
 * 此外，ContextManager 委托给 AbstractTracerContext 的所有主要方法。)
 * </pre>
 */
public class ContextManager implements BootService {
    private static final String EMPTY_TRACE_CONTEXT_ID = "N/A";
    private static final ILog LOGGER = LogManager.getLogger(ContextManager.class);
    /** 线程变量，委托类 TracerContext */
    private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();
    /** 线程变量，用于单个线程中的 上下文 传播？ */
    private static ThreadLocal<RuntimeContext> RUNTIME_CONTEXT = new ThreadLocal<RuntimeContext>();
    /** BootService，上下文管理扩展服务 */
    private static ContextManagerExtendService EXTEND_SERVICE;

    /**
     * 获取 AbstractTracerContext 对象。若不存在，进行创建。
     *
     * @param operationName 操作名
     * @param forceSampling 是否强制收集
     * @return AbstractTracerContext 对象
     */
    private static AbstractTracerContext getOrCreate(String operationName, boolean forceSampling) {
        AbstractTracerContext context = CONTEXT.get();
        if (context == null) {
            if (StringUtil.isEmpty(operationName)) {
                if (LOGGER.isDebugEnable()) {
                    LOGGER.debug("No operation name, ignore this trace.");
                }
                // 操作名空，创建 IgnoredTracerContext 对象
                context = new IgnoredTracerContext();
            } else {
                if (EXTEND_SERVICE == null) {
                    EXTEND_SERVICE = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
                }
                // 创建 TracingContext 对象
                context = EXTEND_SERVICE.createTraceContext(operationName, forceSampling);

            }
            CONTEXT.set(context);
        }
        return context;
    }

    private static AbstractTracerContext get() {
        return CONTEXT.get();
    }

    /**
     * @return the first global trace id when tracing. Otherwise, "N/A".
     */
    public static String getGlobalTraceId() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getReadablePrimaryTraceId() : EMPTY_TRACE_CONTEXT_ID;
    }

    /**
     * @return the current segment id when tracing. Otherwise, "N/A".
     */
    public static String getSegmentId() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getSegmentId() : EMPTY_TRACE_CONTEXT_ID;
    }

    /**
     * @return the current span id when tracing. Otherwise, the value is -1.
     */
    public static int getSpanId() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getSpanId() : -1;
    }

    /**
     * @return the current primary endpoint name. Otherwise, the value is null.
     */
    public static String getPrimaryEndpointName() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getPrimaryEndpointName() : null;
    }

    /**
     * 创建一个 entry Span，并从 ContextCarrier 中 提取 上下文信息。
     */
    public static AbstractSpan createEntrySpan(String operationName, ContextCarrier carrier) {
        AbstractSpan span;
        AbstractTracerContext context;
        // 对操作名称进行截断处理，确保其长度不超过阈值
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        // 如果上下文载体不为空且有效
        if (carrier != null && carrier.isValid()) {
            // 获取采样服务，并强制采样
            SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
            samplingService.forceSampled();
            // 获取或创建一个新的 TracerContext
            context = getOrCreate(operationName, true);
            // 创建入口 Span
            span = context.createEntrySpan(operationName);
            // 从上下文载体中提取追踪信息
            context.extract(carrier);
        } else {
            // 获取或创建一个新的 TracerContext
            context = getOrCreate(operationName, false);
            // 创建入口 Span
            span = context.createEntrySpan(operationName);
        }
        return span;
    }

    /**
     * 创建一个本地 Span
     */
    public static AbstractSpan createLocalSpan(String operationName) {
        // 截断操作名称，确保其长度不超过阈值
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        // 获取或创建一个新的 TracerContext
        AbstractTracerContext context = getOrCreate(operationName, false);
        // 在当前 TracerContext 中创建一个 本地 Span
        return context.createLocalSpan(operationName);
    }

    /**
     * 创建一个 exit Span，并将 上下文信息 注入到 ContextCarrier 中
     */
    public static AbstractSpan createExitSpan(String operationName, ContextCarrier carrier, String remotePeer) {
        // ContextCarrier 不能为空
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        // 截断操作名称，确保其长度不超过阈值
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        // 获取或创建一个新的 TracerContext
        AbstractTracerContext context = getOrCreate(operationName, false);
        // 在当前 TracerContext 中创建一个 exit Span
        AbstractSpan span = context.createExitSpan(operationName, remotePeer);
        // 将当前 Span 的 上下文信息 注入 到 ContextCarrier 中
        context.inject(carrier);
        // 返回创建的 Span
        return span;
    }

    /**
     * 创建一个 exit Span
     */
    public static AbstractSpan createExitSpan(String operationName, String remotePeer) {
        // 截断操作名称，确保其长度不超过阈值
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        // 获取或创建一个新的 TracerContext
        AbstractTracerContext context = getOrCreate(operationName, false);
        // 在当前 TracerContext 中创建一个 exit Span，并返回
        return context.createExitSpan(operationName, remotePeer);
    }

    public static void inject(ContextCarrier carrier) {
        // 委托给 TracerContext 执行 注入
        get().inject(carrier);
    }

    public static void extract(ContextCarrier carrier) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        if (carrier.isValid()) {
            // 委托给 TracerContext 执行 提取
            get().extract(carrier);
        }
    }

    public static ContextSnapshot capture() {
        // 委托给 TracerContext 执行 capture
        return get().capture();
    }

    public static void continued(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("ContextSnapshot can't be null.");
        }
        if (!snapshot.isFromCurrent()) {
            // Invalid snapshot is only created by {@link IgnoredTracerContext#capture()}.
            // When the snapshot is not valid, need to force ignoring the current tracing context.
            if (snapshot.isValid()) {
                // 委托给 TracerContext 执行 continued
                get().continued(snapshot);
            } else {
                AbstractTracerContext context = get().forceIgnoring();
                CONTEXT.set(context);
            }
        }
    }

    /**
     * 等待当前 活跃的 span 异步完成
     * @param span 要等待完成的 span
     * @return 当前的 TracerContext
     */
    public static AbstractTracerContext awaitFinishAsync(AbstractSpan span) {
        // 获取当前的 TracerContext
        final AbstractTracerContext context = get();
        // 获取当前上下文中的活跃 Span
        AbstractSpan activeSpan = context.activeSpan();
        if (span != activeSpan) {
            throw new RuntimeException("Span is not the active in current context.");
        }
        // 等待当前活跃的 Span 异步完成
        return context.awaitFinishAsync();
    }

    /**
     * Using this method will cause NPE if active span does not exist. If one is not sure whether there is an active span, use
     * ContextManager::isActive method to determine whether there has the active span.
     *
     * <pre>
     * 获取当前上下文中的活跃 Span。
     * </pre>
     */
    public static AbstractSpan activeSpan() {
        // 获取当前的 TracerContext 并返回其活跃的 Span
        return get().activeSpan();
    }

    /**
     * Recommend use ContextManager::stopSpan(AbstractSpan span), because in that way, the TracingContext core could
     * verify this span is the active one, in order to avoid stop unexpected span. If the current span is hard to get or
     * only could get by low-performance way, this stop way is still acceptable.
     *
     * <pre>
     * 停止 当前活跃的 Span。
     *
     * 推荐使用 ContextManager::stopSpan(AbstractSpan span) 方法，
     *      因为该方法会验证传入的 Span 是否是当前上下文中的活跃 Span，以避免停止错误的 Span。
     *      如果当前 Span 很难获取或者只能通过低性能的方式获取，那么这种方式也是可以接受的。
     *
     * </pre>
     */
    public static void stopSpan() {
        final AbstractTracerContext context = get();
        stopSpan(context.activeSpan(), context);
    }

    public static void stopSpan(AbstractSpan span) {
        stopSpan(span, get());
    }

    private static void stopSpan(AbstractSpan span, final AbstractTracerContext context) {
        // 停止 span，该span 出栈后，若 activeSpanStack 为空，则返回 true
        if (context.stopSpan(span)) {
            // 若 activeSpanStack 为空，移除 线程变量 CONTEXT 和 RUNTIME_CONTEXT。
            CONTEXT.remove();
            RUNTIME_CONTEXT.remove();
        }
    }

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }

    public static boolean isActive() {
        return get() != null;
    }

    public static RuntimeContext getRuntimeContext() {
        RuntimeContext runtimeContext = RUNTIME_CONTEXT.get();
        if (runtimeContext == null) {
            runtimeContext = new RuntimeContext(RUNTIME_CONTEXT);
            RUNTIME_CONTEXT.set(runtimeContext);
        }

        return runtimeContext;
    }

    public static CorrelationContext getCorrelationContext() {
        final AbstractTracerContext tracerContext = get();
        if (tracerContext == null) {
            return null;
        }

        return tracerContext.getCorrelationContext();
    }

}
