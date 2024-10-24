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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.dynamic.watcher.SpanLimitWatcher;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.EntrySpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitTypeSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LocalSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.profile.ProfileStatusContext;
import org.apache.skywalking.apm.agent.core.profile.ProfileTaskExecutionService;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.Agent.CLUSTER;

/**
 * The <code>TracingContext</code> represents a core tracing logic controller. It build the final {@link
 * TracingContext}, by the stack mechanism, which is similar with the codes work.
 * <p>
 * In opentracing concept, it means, all spans in a segment tracing context(thread) are CHILD_OF relationship, but no
 * FOLLOW_OF.
 * <p>
 * In skywalking core concept, FOLLOW_OF is an abstract concept when cross-process MQ or cross-thread async/batch tasks
 * happen, we used {@link TraceSegmentRef} for these scenarios. Check {@link TraceSegmentRef} which is from {@link
 * ContextCarrier} or {@link ContextSnapshot}.
 */
public class TracingContext implements AbstractTracerContext {
    private static final ILog LOGGER = LogManager.getLogger(TracingContext.class);
    private long lastWarningTimestamp = 0;

    /**
     * @see ProfileTaskExecutionService
     */
    private static ProfileTaskExecutionService PROFILE_TASK_EXECUTION_SERVICE;

    /**
     * The final {@link TraceSegment}, which includes all finished spans.
     * <pre>
     * (最后的 TraceSegment，其中包括所有已完成的 span。)
     * </pre>
     */
    private TraceSegment segment;

    /**
     * Active spans stored in a Stack, usually called 'ActiveSpanStack'. This {@link LinkedList} is the in-memory
     * storage-structure. <p> I use {@link LinkedList#removeLast()}, {@link LinkedList#addLast(Object)} and {@link
     * LinkedList#getLast()} instead of {@link #pop()}, {@link #push(AbstractSpan)}, {@link #peek()}
     *
     * <pre>
     * (活动的spans 存储在堆栈中，通常称为“ActiveSpanStack”。这个 LinkedList 是内存中的存储结构。
     * 我用 LinkedList.removeLast()、LinkedList.addLast(Object) 和 LinkedList.getLast() 代替 pop(), push(AbstractSpan), peek() )
     * </pre>
     */
    private LinkedList<AbstractSpan> activeSpanStack = new LinkedList<>();

    /**
     * @since 8.10.0 replace the removed "firstSpan"(before 8.10.0) reference. see {@link PrimaryEndpoint} for more details.
     */
    private PrimaryEndpoint primaryEndpoint = null;

    /**
     * A counter for the next span.
     * <pre>
     * (下一个 span 的计数器。)
     * </pre>
     */
    private int spanIdGenerator;

    /**
     * The counter indicates
     */
    @SuppressWarnings("unused") // updated by ASYNC_SPAN_COUNTER_UPDATER
    private volatile int asyncSpanCounter;
    private static final AtomicIntegerFieldUpdater<TracingContext> ASYNC_SPAN_COUNTER_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(TracingContext.class, "asyncSpanCounter");
    private volatile boolean isRunningInAsyncMode;
    private volatile ReentrantLock asyncFinishLock;

    private volatile boolean running;

    private final long createTime;

    /**
     * profile status
     */
    private final ProfileStatusContext profileStatus;
    /** 关联上下文 */
    @Getter(AccessLevel.PACKAGE)
    private final CorrelationContext correlationContext;
    /** 扩展上下文 */
    @Getter(AccessLevel.PACKAGE)
    private final ExtensionContext extensionContext;

    //CDS watcher
    private final SpanLimitWatcher spanLimitWatcher;

    /**
     * Initialize all fields with default value.
     * <pre>
     * (使用默认值初始化所有字段。)
     * </pre>
     *
     * @param spanLimitWatcher Span数目限制观察者
     */
    TracingContext(String firstOPName, SpanLimitWatcher spanLimitWatcher) {
        this.segment = new TraceSegment();
        this.spanIdGenerator = 0;
        isRunningInAsyncMode = false;
        createTime = System.currentTimeMillis();
        running = true;

        // profiling status
        // 性能分析执行器服务
        if (PROFILE_TASK_EXECUTION_SERVICE == null) {
            PROFILE_TASK_EXECUTION_SERVICE = ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class);
        }
        // 开始 当前TracingContext 的性能分析？
        this.profileStatus = PROFILE_TASK_EXECUTION_SERVICE.addProfiling(
            this, segment.getTraceSegmentId(), firstOPName);

        this.correlationContext = new CorrelationContext();
        this.extensionContext = new ExtensionContext();
        this.spanLimitWatcher = spanLimitWatcher;
    }

    /**
     * Inject the context into the given carrier, only when the active span is an exit one.
     * <pre>
     * (仅当 active span 是 exit 时，才将 context 注入 给定的 carrier。)
     * </pre>
     *
     * @param carrier to carry the context for crossing process.
     * @throws IllegalStateException if (1) the active span isn't an exit one. (2) doesn't include peer. Ref to {@link
     *                               AbstractTracerContext#inject(ContextCarrier)}
     */
    @Override
    public void inject(ContextCarrier carrier) {
        // 将当前 active span 注入到 carrier
        this.inject(this.activeSpan(), carrier);
    }

    /**
     * Inject the context into the given carrier and given span, only when the active span is an exit one. This method
     * wouldn't be opened in {@link ContextManager} like {@link #inject(ContextCarrier)}, it is only supported to be
     * called inside the {@link ExitTypeSpan#inject(ContextCarrier)}
     *
     * <pre>
     * (将 上下文 注入到 给定的载体 和 给定的Span 中，仅当活动的 Span 是出口 Span 时才执行此操作。
     *  这个方法不会在 {@link ContextManager} 中开放，类似于 {@link #inject(ContextCarrier)}，
     *  它只支持在 {@link ExitTypeSpan#inject(ContextCarrier)} 内部调用。)
     * </pre>
     *
     * @param carrier  to carry the context for crossing process.
     * @param exitSpan to represent the scope of current injection.
     * @throws IllegalStateException if (1) the span isn't an exit one. (2) doesn't include peer.
     */
    public void inject(AbstractSpan exitSpan, ContextCarrier carrier) {
        // 检查给定的 Span 是否是出口 Span
        if (!exitSpan.isExit()) {
            throw new IllegalStateException("Inject can be done only in Exit Span");
        }

        // 将给定的 Span 转换为 ExitTypeSpan
        ExitTypeSpan spanWithPeer = (ExitTypeSpan) exitSpan;
        // 检查 对端信息 是否为空
        String peer = spanWithPeer.getPeer();
        if (StringUtil.isEmpty(peer)) {
            throw new IllegalStateException("Exit span doesn't include meaningful peer information.");
        }

        // 为 carrier 的属性设置值（方便后续 carrier 的序列化（carrier.item 中进行））
        carrier.setTraceId(getReadablePrimaryTraceId());
        carrier.setTraceSegmentId(this.segment.getTraceSegmentId());
        carrier.setSpanId(exitSpan.getSpanId());
        carrier.setParentService(Config.Agent.SERVICE_NAME);
        carrier.setParentServiceInstance(Config.Agent.INSTANCE_NAME);
        carrier.setParentEndpoint(primaryEndpoint.getName());
        carrier.setAddressUsedAtClient(peer); // 设置客户端使用的地址

        // 将 关联上下文 注入到 载体 中
        this.correlationContext.inject(carrier);
        // 将 扩展上下文 注入到 载体 中
        this.extensionContext.inject(carrier);
    }

    /**
     * Extract the carrier to build the reference for the pre segment.
     * <pre>
     * (提取 carrier 以构建 pre segment 的引用。)
     * </pre>
     *
     * @param carrier carried the context from a cross-process segment. Ref to {@link AbstractTracerContext#extract(ContextCarrier)}
     */
    @Override
    public void extract(ContextCarrier carrier) {
        // 从 ContextCarrier 中提取追踪信息，并创建一个 TraceSegmentRef 对象
        TraceSegmentRef ref = new TraceSegmentRef(carrier);
        // 将提取的 引用信息 添加到当前的 TraceSegment 中
        this.segment.ref(ref);
        // 将全局追踪 ID 添加到当前的 TraceSegment 中
        this.segment.relatedGlobalTrace(new PropagatedTraceId(carrier.getTraceId()));
        // 获取当前活动的 Span
        AbstractSpan span = this.activeSpan();
        // 如果当前活动的 Span 是 EntrySpan，则将提取的引用信息添加到该 Span 中
        if (span instanceof EntrySpan) {
            span.ref(ref);
        }

        // 从 ContextCarrier 中提取 扩展信息 并添加到当前 TracingContext 中
        carrier.extractExtensionTo(this);
        // 从 ContextCarrier 中提取 关联信息 并添加到当前 TracingContext 中
        carrier.extractCorrelationTo(this);
    }

    /**
     * Capture the snapshot of current context.
     * <pre>
     * (捕获当前上下文的快照。)
     * </pre>
     *
     * @return the snapshot of context for cross-thread propagation Ref to {@link AbstractTracerContext#capture()}
     */
    @Override
    public ContextSnapshot capture() {
        // 创建一个 ContextSnapshot 对象
        ContextSnapshot snapshot = new ContextSnapshot(
            segment.getTraceSegmentId(),  // 当前 TraceSegment 的 ID
            activeSpan().getSpanId(),     // 当前活动 Span 的 ID
            getPrimaryTraceId(),          // 主 Trace ID
            primaryEndpoint.getName(),    // 主端点名称
            this.correlationContext,      // 关联上下文
            this.extensionContext,        // 扩展上下文
            this.profileStatus            // 采样状态
        );

        return snapshot;
    }

    /**
     * Continue the context from the given snapshot of parent thread.
     * <pre>
     * (从给定的 父线程快照 中 继续 上下文。)
     * </pre>
     *
     * @param snapshot from {@link #capture()} in the parent thread. Ref to {@link AbstractTracerContext#continued(ContextSnapshot)}
     */
    @Override
    public void continued(ContextSnapshot snapshot) {
        // 检查快照是否有效
        if (snapshot.isValid()) {
            // 从快照中创建一个 TraceSegmentRef 对象
            TraceSegmentRef segmentRef = new TraceSegmentRef(snapshot);
            // 将 引用信息 添加到 当前的 TraceSegment 中
            this.segment.ref(segmentRef);
            // 将 引用信息 添加到 当前活动的 Span 中
            this.activeSpan().ref(segmentRef);
            // 将 全局追踪ID 添加到当前的 TraceSegment 中
            this.segment.relatedGlobalTrace(snapshot.getTraceId());
            // 从 快照 中 继续 关联上下文
            this.correlationContext.continued(snapshot);
            // 从 快照 中 继续 扩展上下文
            this.extensionContext.continued(snapshot);
            // 处理 当前活动的 Span
            this.extensionContext.handle(this.activeSpan());
            // 如果 采样状态 可以从快照中继续
            if (this.profileStatus.continued(snapshot)) {
                // 继续采样任务
                PROFILE_TASK_EXECUTION_SERVICE.continueProfiling(this, this.segment.getTraceSegmentId());
            }
        }
    }

    /**
     * @return the first global trace id.
     */
    @Override
    public String getReadablePrimaryTraceId() {
        return getPrimaryTraceId().getId();
    }

    private DistributedTraceId getPrimaryTraceId() {
        return segment.getRelatedGlobalTrace();
    }

    @Override
    public String getSegmentId() {
        return segment.getTraceSegmentId();
    }

    @Override
    public int getSpanId() {
        return activeSpan().getSpanId();
    }

    /**
     * Create an entry span
     *
     * @param operationName most likely a service name
     * @return span instance. Ref to {@link EntrySpan}
     */
    @Override
    public AbstractSpan createEntrySpan(final String operationName) {
        // 如果限流机制正在工作，则创建一个 NoopSpan 并返回 QFTODO
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }
        AbstractSpan entrySpan;
        TracingContext owner = this;

        // 获取当前栈顶的 Span
        final AbstractSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();

        // 如果父 Span 存在，且是入口 Span
        if (parentSpan != null && parentSpan.isEntry()) {
            /**
             * Only add the profiling recheck on creating entry span,
             * as the operation name could be overrided.
             * （仅在创建入口 Span 时添加性能分析重新检查，因为操作名称可能会被覆盖。）
             */
            // 性能分析重新检查
            profilingRecheck(parentSpan, operationName);
            // 设置操作名称
            parentSpan.setOperationName(operationName);
            // 使用父 Span 作为入口 Span
            entrySpan = parentSpan;
            // 启动并返回入口 Span
            return entrySpan.start();
        } else {
            // 创建一个新的 EntrySpan
            entrySpan = new EntrySpan(
                spanIdGenerator++, // 生成新的 Span ID
                parentSpanId, // 父 Span ID
                operationName, // 操作名称
                owner // 当前 TracingContext
            );
            // 启动新创建的 EntrySpan
            entrySpan.start();
            // 将新创建的 EntrySpan 推入栈中并返回
            return push(entrySpan);
        }
    }

    /**
     * Create a local span
     *
     * @param operationName most likely a local method signature, or business name.
     * @return the span represents a local logic block. Ref to {@link LocalSpan}
     */
    @Override
    public AbstractSpan createLocalSpan(final String operationName) {
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }
        AbstractSpan parentSpan = peek();
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        AbstractTracingSpan span = new LocalSpan(spanIdGenerator++, parentSpanId, operationName, this);
        span.start();
        return push(span);
    }

    /**
     * Create an exit span
     *
     * @param operationName most likely a service name of remote
     * @param remotePeer    the network id(ip:port, hostname:port or ip1:port1,ip2,port, etc.). Remote peer could be set
     *                      later, but must be before injecting.
     * @return the span represent an exit point of this segment.
     * @see ExitSpan
     */
    @Override
    public AbstractSpan createExitSpan(final String operationName, String remotePeer) {
        if (isLimitMechanismWorking()) {
            NoopExitSpan span = new NoopExitSpan(remotePeer);
            return push(span);
        }

        AbstractSpan exitSpan;
        AbstractSpan parentSpan = peek();
        TracingContext owner = this;
        if (parentSpan != null && parentSpan.isExit()) {
            exitSpan = parentSpan;
        } else {
            // Since 8.10.0
            remotePeer = StringUtil.isEmpty(CLUSTER) ? remotePeer : CLUSTER + "/" + remotePeer;
            final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
            exitSpan = new ExitSpan(spanIdGenerator++, parentSpanId, operationName, remotePeer, owner);
            push(exitSpan);
        }
        exitSpan.start();
        return exitSpan;
    }

    /**
     * @return the active span of current context, the top element of {@link #activeSpanStack}
     */
    @Override
    public AbstractSpan activeSpan() {
        AbstractSpan span = peek();
        if (span == null) {
            throw new IllegalStateException("No active span.");
        }
        return span;
    }

    /**
     * Stop the given span, if and only if this one is the top element of {@link #activeSpanStack}. Because the tracing
     * core must make sure the span must match in a stack module, like any program did.
     *
     * @param span to finish
     */
    @Override
    public boolean stopSpan(AbstractSpan span) {
        AbstractSpan lastSpan = peek();
        if (lastSpan == span) {
            if (lastSpan instanceof AbstractTracingSpan) {
                AbstractTracingSpan toFinishSpan = (AbstractTracingSpan) lastSpan;
                // 结束 last span，将 该 span 加入到 当前的 TraceSegment，并从 TracingContext 的栈中弹出
                if (toFinishSpan.finish(segment)) {
                    pop();
                }
            } else {
                // 如果不是 AbstractTracingSpan 类型的 span，则直接弹出
                pop();
            }
        } else {
            throw new IllegalStateException("Stopping the unexpected span = " + span);
        }

        // 结束 segment（segment#finish），并通知 TracingThreadListener 和 TracingContextListener，当前上下文结束。
        finish();

        // 如果 activeSpanStack 为空，则返回 true，（为 CONTEXT  和 RUNTIME_CONTEXT 的 remove 做准备）
        return activeSpanStack.isEmpty();
    }

    @Override
    public AbstractTracerContext awaitFinishAsync() {
        if (!isRunningInAsyncMode) {
            synchronized (this) {
                if (!isRunningInAsyncMode) {
                    asyncFinishLock = new ReentrantLock();
                    ASYNC_SPAN_COUNTER_UPDATER.set(this, 0);
                    isRunningInAsyncMode = true;
                }
            }
        }
        ASYNC_SPAN_COUNTER_UPDATER.incrementAndGet(this);
        return this;
    }

    @Override
    public void asyncStop(AsyncSpan span) {
        ASYNC_SPAN_COUNTER_UPDATER.decrementAndGet(this);
        finish();
    }

    @Override
    public CorrelationContext getCorrelationContext() {
        return this.correlationContext;
    }

    @Override
    public String getPrimaryEndpointName() {
        return primaryEndpoint.getName();
    }

    @Override
    public AbstractTracerContext forceIgnoring() {
        for (AbstractSpan span: activeSpanStack) {
            span.forceIgnoring();
        }
        return new IgnoredTracerContext(activeSpanStack.size());
    }

    /**
     * Re-check current trace need profiling, encase third part plugin change the operation name.
     *
     * @param span          current modify span
     * @param operationName change to operation name
     */
    public void profilingRecheck(AbstractSpan span, String operationName) {
        // only recheck first span
        if (span.getSpanId() != 0) {
            return;
        }

        PROFILE_TASK_EXECUTION_SERVICE.profilingRecheck(this, segment.getTraceSegmentId(), operationName);
    }

    /**
     * Finish this context, and notify all {@link TracingContextListener}s, managed by {@link
     * TracingContext.ListenerManager} and {@link TracingContext.TracingThreadListenerManager}
     * <pre>
     * (完成此上下文，并通知所有由 ListenerManager 和 TracingThreadListenerManager 管理的 TracingContextListeners)
     * </pre>
     */
    private void finish() {
        if (isRunningInAsyncMode) {
            asyncFinishLock.lock();
        }
        try {
            boolean isFinishedInMainThread = activeSpanStack.isEmpty() && running;
            if (isFinishedInMainThread) {
                // Notify after tracing finished in the main thread.
                // 通知 TracingThreadListener
                TracingThreadListenerManager.notifyFinish(this);
            }

            if (isFinishedInMainThread && (!isRunningInAsyncMode || asyncSpanCounter == 0)) {
                // 结束 segment
                TraceSegment finishedSegment = segment.finish(isLimitMechanismWorking());
                // 通知 TracingContextListener（方便 TraceSegmentServiceClient.afterFinished 发送 TraceSegment 到 OAP）
                TracingContext.ListenerManager.notifyFinish(finishedSegment);
                running = false;
            }
        } finally {
            if (isRunningInAsyncMode) {
                asyncFinishLock.unlock();
            }
        }
    }

    /**
     * The <code>ListenerManager</code> represents an event notify for every registered listener, which are notified
     * when the <code>TracingContext</code> finished, and {@link #segment} is ready for further process.
     * <pre>
     * (ListenerManager 表示每个注册侦听器的事件通知，当 TracingContext 完成并且segment准备好进行进一步处理时，将通知侦听器。)
     * </pre>
     */
    public static class ListenerManager {
        private static List<TracingContextListener> LISTENERS = new LinkedList<>();

        /**
         * Add the given {@link TracingContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(TracingContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link TracingContext.ListenerManager} about the given {@link TraceSegment} have finished. And
         * trigger {@link TracingContext.ListenerManager} to notify all {@link #LISTENERS} 's {@link
         * TracingContextListener#afterFinished(TraceSegment)}
         *
         * @param finishedSegment the segment that has finished
         */
        static void notifyFinish(TraceSegment finishedSegment) {
            for (TracingContextListener listener : LISTENERS) {
                listener.afterFinished(finishedSegment);
            }
        }

        /**
         * Clear the given {@link TracingContextListener}
         */
        public static synchronized void remove(TracingContextListener listener) {
            LISTENERS.remove(listener);
        }

    }

    /**
     * The <code>ListenerManager</code> represents an event notify for every registered listener, which are notified
     */
    public static class TracingThreadListenerManager {
        private static List<TracingThreadListener> LISTENERS = new LinkedList<>();

        public static synchronized void add(TracingThreadListener listener) {
            LISTENERS.add(listener);
        }

        static void notifyFinish(TracingContext finishedContext) {
            for (TracingThreadListener listener : LISTENERS) {
                listener.afterMainThreadFinish(finishedContext);
            }
        }

        public static synchronized void remove(TracingThreadListener listener) {
            LISTENERS.remove(listener);
        }
    }

    /**
     * @return the top element of 'ActiveSpanStack', and remove it.
     */
    private AbstractSpan pop() {
        return activeSpanStack.removeLast();
    }

    /**
     * Add a new Span at the top of 'ActiveSpanStack'
     *
     * @param span the {@code span} to push
     */
    private AbstractSpan push(AbstractSpan span) {
        if (primaryEndpoint == null) {
            primaryEndpoint = new PrimaryEndpoint(span);
        } else {
            primaryEndpoint.set(span);
        }
        activeSpanStack.addLast(span);
        this.extensionContext.handle(span);
        return span;
    }

    /**
     * @return the top element of 'ActiveSpanStack' only.
     */
    private AbstractSpan peek() {
        if (activeSpanStack.isEmpty()) {
            return null;
        }
        return activeSpanStack.getLast();
    }

    private boolean isLimitMechanismWorking() {
        if (spanIdGenerator >= spanLimitWatcher.getSpanLimit()) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastWarningTimestamp > 30 * 1000) {
                LOGGER.warn(
                    new RuntimeException("Shadow tracing context. Thread dump"),
                    "More than {} spans required to create", spanLimitWatcher.getSpanLimit()
                );
                lastWarningTimestamp = currentTimeMillis;
            }
            return true;
        } else {
            return false;
        }
    }

    public long createTime() {
        return this.createTime;
    }

    public ProfileStatusContext profileStatus() {
        return this.profileStatus;
    }

    /**
     * Primary endpoint name is used for endpoint dependency. The name pick policy according to priority is
     * 1. Use the first entry span's operation name
     * 2. Use the first span's operation name
     *
     * @since 8.10.0
     */
    private class PrimaryEndpoint {
        @Getter
        private AbstractSpan span;

        private PrimaryEndpoint(final AbstractSpan span) {
            this.span = span;
        }

        /**
         * Set endpoint name according to priority
         */
        private void set(final AbstractSpan span) {
            if (!this.span.isEntry() && span.isEntry()) {
                this.span = span;
            }
        }

        private String getName() {
            return span.getOperationName();
        }
    }
}
