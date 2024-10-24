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

package org.apache.skywalking.apm.agent.core.context.trace;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.apache.skywalking.apm.agent.core.context.ids.GlobalIdGenerator;
import org.apache.skywalking.apm.agent.core.context.ids.NewDistributedTraceId;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;

/**
 * {@link TraceSegment} is a segment or fragment of the distributed trace. See https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model
 * A {@link TraceSegment} means the segment, which exists in current {@link Thread}. And the distributed trace is formed
 * by multi {@link TraceSegment}s, because the distributed trace crosses multi-processes, multi-threads. <p>
 *
 * <pre>
 * (TraceSegment 是分布式跟踪的一个 segment 或 fragment（片段）。
 *      参见 https://github.com/opentracing/specification/blob/master/specification.md#the-opentracing-data-model。
 * TraceSegment 表示存在于当前线程中的 segment。
 * 分布式跟踪是由多个 TraceSegment 组成的，因为分布式跟踪是跨多进程、多线程的。)
 * </pre>
 */
public class TraceSegment {
    /**
     * The id of this trace segment. Every segment has its unique-global-id.
     * （这个 trace segment 的 id。每个 segment 都有其唯一的 global-id 。）
     */
    private String traceSegmentId;

    /**
     * The refs of parent trace segments, except the primary one. For most RPC call, {@link #ref} contains only one
     * element, but if this segment is a start span of batch process, the segment faces multi parents, at this moment,
     * we only cache the first parent segment reference.
     * <p>
     * This field will not be serialized. Keeping this field is only for quick accessing.
     *
     * <pre>
     * (父跟踪段的引用，主跟踪段除外。
     * 对于大多数RPC调用，ref只包含一个元素，但是如果这个 segment 是批处理进程的 start span ，则 segment 面对多个 父segment，此时，我们只缓存第一个 父段 引用。
     * 该字段不会被序列化。保留此字段仅供快速访问。)
     * </pre>
     */
    private TraceSegmentRef ref;

    /**
     * The spans belong to this trace segment. They all have finished. All active spans are hold and controlled by
     * "skywalking-api" module.
     * （spans 属于这个 trace segment 。他们都 完成 了。所有 active spans 都由 skywalking-api 模块 保持 和 控制。）
     */
    private List<AbstractTracingSpan> spans;

    /**
     * The <code>relatedGlobalTraceId</code> represent the related trace. Most time it related only one
     * element, because only one parent {@link TraceSegment} exists, but, in batch scenario, the num becomes greater
     * than 1, also meaning multi-parents {@link TraceSegment}. But we only related the first parent TraceSegment.
     *
     * <pre>
     * (relatedGlobalTraceId 表示相关的 trace 。
     * 大多数情况下，它只与一个元素相关，因为只有一个 父TraceSegment 存在，
     * 但是，在批处理场景中，num大于1，也意味着多 父TraceSegment。但是我们只关联了第一个父TraceSegment。)
     *
     * 分布式跟踪 ID
     * </pre>
     */
    private DistributedTraceId relatedGlobalTraceId;

    private boolean ignore = false;

    private boolean isSizeLimited = false;

    private final long createTime;

    /**
     * Create a default/empty trace segment, with current time as start time, and generate a new segment id.
     */
    public TraceSegment() {
        this.traceSegmentId = GlobalIdGenerator.generate();
        this.spans = new LinkedList<>();
        this.relatedGlobalTraceId = new NewDistributedTraceId();
        this.createTime = System.currentTimeMillis();
    }

    /**
     * Establish the link between this segment and its parents.
     *
     * @param refSegment {@link TraceSegmentRef}
     */
    public void ref(TraceSegmentRef refSegment) {
        if (null == ref) {
            this.ref = refSegment;
        }
    }

    /**
     * Establish the line between this segment and the relative global trace id.
     */
    public void relatedGlobalTrace(DistributedTraceId distributedTraceId) {
        if (relatedGlobalTraceId instanceof NewDistributedTraceId) {
            this.relatedGlobalTraceId = distributedTraceId;
        }
    }

    /**
     * After {@link AbstractSpan} is finished, as be controller by "skywalking-api" module, notify the {@link
     * TraceSegment} to archive it.
     * <pre>
     * (AbstractSpan 完成后，作为“skywalking-api”模块的控制器，通知 TraceSegment 对其进行归档。)
     * </pre>
     */
    public void archive(AbstractTracingSpan finishedSpan) {
        spans.add(finishedSpan);
    }

    /**
     * Finish this {@link TraceSegment}. <p> return this, for chaining
     */
    public TraceSegment finish(boolean isSizeLimited) {
        this.isSizeLimited = isSizeLimited;
        return this;
    }

    public String getTraceSegmentId() {
        return traceSegmentId;
    }

    /**
     * Get the first parent segment reference.
     */
    public TraceSegmentRef getRef() {
        return ref;
    }

    public DistributedTraceId getRelatedGlobalTrace() {
        return relatedGlobalTraceId;
    }

    public boolean isSingleSpanSegment() {
        return this.spans != null && this.spans.size() == 1;
    }

    public boolean isIgnore() {
        return ignore;
    }

    public void setIgnore(boolean ignore) {
        this.ignore = ignore;
    }

    /**
     * This is a high CPU cost method, only called when sending to collector or test cases.
     * <pre>
     * (这是一个高CPU开销的方法，只有在发送到收集器或测试用例时才调用)
     *
     * ConsumerThread.dataSources.QueueBuffer 中的 data 在 TraceSegmentServiceClient.consume(List<TraceSegment>) 中被消费。
     * </pre>
     *
     * @return the segment as GRPC service parameter
     */
    public SegmentObject transform() {
        SegmentObject.Builder traceSegmentBuilder = SegmentObject.newBuilder();
        traceSegmentBuilder.setTraceId(getRelatedGlobalTrace().getId());
        /*
         * Trace Segment
         */
        traceSegmentBuilder.setTraceSegmentId(this.traceSegmentId);
        // Don't serialize TraceSegmentReference

        // SpanObject
        for (AbstractTracingSpan span : this.spans) {
            traceSegmentBuilder.addSpans(span.transform());
        }
        traceSegmentBuilder.setService(Config.Agent.SERVICE_NAME);
        traceSegmentBuilder.setServiceInstance(Config.Agent.INSTANCE_NAME);
        traceSegmentBuilder.setIsSizeLimited(this.isSizeLimited);

        return traceSegmentBuilder.build();
    }

    @Override
    public String toString() {
        return "TraceSegment{" + "traceSegmentId='" + traceSegmentId + '\'' + ", ref=" + ref + ", spans=" + spans + "}";
    }

    public long createTime() {
        return this.createTime;
    }
}
