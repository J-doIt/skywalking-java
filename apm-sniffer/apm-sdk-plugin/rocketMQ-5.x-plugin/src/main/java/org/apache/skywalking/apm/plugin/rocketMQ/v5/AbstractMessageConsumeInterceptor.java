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

package org.apache.skywalking.apm.plugin.rocketMQ.v5;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageExt;
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
import org.apache.skywalking.apm.plugin.rocketMQ.v5.define.ConsumerEnhanceInfos;

/**
 * {@link AbstractMessageConsumeInterceptor} create entry span when the <code>consumeMessage</code> in the {@link
 * org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently} and {@link
 * org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly} class.
 *
 * <pre>
 * (AbstractMessageConsumeInterceptor 提供了在消息消费前拦截的逻辑，
 *  用于创建 跟踪跨度（span）并设置相关的标签（tag）信息，以监控 RocketMQ 消费者的行为。)
 * </pre>
 *
 *
 * <pre>
 * 增强类：MessageListenerConcurrently 或 MessageListenerOrderly
 * 增强方法：... consumeMessage(List<MessageExt> msgs, ...)
 * </pre>
 */
public abstract class AbstractMessageConsumeInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String CONSUMER_OPERATION_NAME_PREFIX = "RocketMQ/";

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        // 获取消息列表
        List<MessageExt> msgs = (List<MessageExt>) allArguments[0];

        // 从第一条消息中提取上下文载体（ContextCarrier）
        ContextCarrier contextCarrier = getContextCarrierFromMessage(msgs.get(0));

        // 创建入口跟踪跨度，使用主题作为操作名称的一部分
        AbstractSpan span = ContextManager.createEntrySpan(CONSUMER_OPERATION_NAME_PREFIX + msgs.get(0)
                                                                                                .getTopic() + "/Consumer", contextCarrier);
        // 设置MQ主题标签
        Tags.MQ_TOPIC.set(span, msgs.get(0).getTopic());

        // 如果broker地址非空
        if (msgs.get(0).getStoreHost() != null) {
            String brokerAddress = msgs.get(0).getStoreHost().toString();
            brokerAddress = StringUtils.removeStart(brokerAddress, "/");
            // 设置 MQ Broker 标签
            Tags.MQ_BROKER.set(span, brokerAddress);
        }

        // 设置组件信息为 rocketMQ-consumer
        span.setComponent(ComponentsDefine.ROCKET_MQ_CONSUMER);

        // 标记层为消息队列
        SpanLayer.asMQ(span);

        // 遍历剩余消息，从 MessageExt 中提取 SkyWalking 的追踪上下文（ContextCarrier），并设置到 contextCarrier.items 的 HeadValue
        for (int i = 1; i < msgs.size(); i++) {
            ContextManager.extract(getContextCarrierFromMessage(msgs.get(i)));
        }

        // 从动态字段中获取并设置 Consumer 的 Namesrv 地址
        Object skyWalkingDynamicField = objInst.getSkyWalkingDynamicField();
        if (skyWalkingDynamicField != null) {
            ConsumerEnhanceInfos consumerEnhanceInfos = (ConsumerEnhanceInfos) skyWalkingDynamicField;
            span.setPeer(consumerEnhanceInfos.getNamesrvAddr());
        }
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }

    /**
     * 将 message.properties 中的 链路信息 设置到 新创建的 ContextCarrier 中
     */
    private ContextCarrier getContextCarrierFromMessage(MessageExt message) {
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(message.getUserProperty(next.getHeadKey()));
        }

        return contextCarrier;
    }
}
