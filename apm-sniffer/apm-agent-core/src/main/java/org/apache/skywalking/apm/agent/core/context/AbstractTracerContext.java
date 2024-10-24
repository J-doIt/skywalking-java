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

import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * The <code>AbstractTracerContext</code> represents the tracer context manager.
 */
public interface AbstractTracerContext {
    /**
     * Prepare for the cross-process propagation. How to initialize the carrier, depends on the implementation.
     * <pre>
     * (准备跨进程传播 。)
     * </pre>
     *
     * @param carrier to carry the context for crossing process.
     */
    void inject(ContextCarrier carrier);

    /**
     * Build the reference between this segment and a cross-process segment. How to build, depends on the
     * implementation.
     * <pre>
     * (在 此segment 和 跨进程segment 之间构建 reference 。)
     * </pre>
     *
     * @param carrier carried the context from a cross-process segment.
     */
    void extract(ContextCarrier carrier);

    /**
     * Capture a snapshot for cross-thread propagation. It's a similar concept with ActiveSpan.Continuation in
     * OpenTracing-java How to build, depends on the implementation.
     * <pre>
     * (捕获用于跨线程传播的 snapshot 。这与 OpenTracing-java 中的 ActiveSpan.Continuation 的概念类似。)
     * </pre>
     *
     * @return the {@link ContextSnapshot} , which includes the reference context.
     */
    ContextSnapshot capture();

    /**
     * Build the reference between this segment and a cross-thread segment. How to build, depends on the
     * implementation.
     *
     * <pre>
     * (构建 引用（reference），在 该 segment 和 一个 跨线程segment 之间。)
     * 在子线程中关联父进程快照需要使用该方法。
     * </pre>
     *
     * @param snapshot from {@link #capture()} in the parent thread.
     */
    void continued(ContextSnapshot snapshot);

    /**
     * Get the global trace id, if needEnhance. How to build, depends on the implementation.
     * <pre>
     * (如果需要增强，获取全局跟踪id。)
     * </pre>
     *
     * @return the string represents the id.
     */
    String getReadablePrimaryTraceId();

    /**
     * Get the current segment id, if needEnhance. How to build, depends on the implementation.
     *
     * @return the string represents the id.
     */
    String getSegmentId();

    /**
     * Get the active span id, if needEnhance. How to build, depends on the implementation.
     *
     * @return the string represents the id.
     */
    int getSpanId();

    /**
     * Create an entry span
     *
     * @param operationName most likely a service name
     * @return the span represents an entry point of this segment.
     */
    AbstractSpan createEntrySpan(String operationName);

    /**
     * Create a local span
     *
     * @param operationName most likely a local method signature, or business name.
     * @return the span represents a local logic block.
     */
    AbstractSpan createLocalSpan(String operationName);

    /**
     * Create an exit span
     *
     * @param operationName most likely a service name of remote
     * @param remotePeer    the network id(ip:port, hostname:port or ip1:port1,ip2,port, etc.). Remote peer could be set
     *                      later, but must be before injecting.
     * @return the span represent an exit point of this segment.
     */
    AbstractSpan createExitSpan(String operationName, String remotePeer);

    /**
     * @return the active span of current tracing context(stack)
     */
    AbstractSpan activeSpan();

    /**
     * Finish the given span, and the given span should be the active span of current tracing context(stack)
     *
     * <pre>
     * (完成给定的 span ，并且给定的 span 应该是当前跟踪上下文（stack）的 active span 。)
     * </pre>
     *
     * @param span to finish
     * @return true when context should be clear.
     */
    boolean stopSpan(AbstractSpan span);

    /**
     * Notify this context, current span is going to be finished async in another thread.
     * <pre>
     * (通知此上下文，当前 span 将在另一个线程中异步完成。)
     * </pre>
     *
     * @return The current context
     */
    AbstractTracerContext awaitFinishAsync();

    /**
     * The given span could be stopped officially.
     * <pre>
     * (给定的 span 可以正式停止。)
     * </pre>
     *
     * @param span to be stopped.
     */
    void asyncStop(AsyncSpan span);

    /**
     * Get current correlation context
     */
    CorrelationContext getCorrelationContext();

    /**
     * Get current primary endpoint name
     */
    String getPrimaryEndpointName();

    /**
     *  Change the current context to be in ignoring status.
     */
    AbstractTracerContext forceIgnoring();

}
