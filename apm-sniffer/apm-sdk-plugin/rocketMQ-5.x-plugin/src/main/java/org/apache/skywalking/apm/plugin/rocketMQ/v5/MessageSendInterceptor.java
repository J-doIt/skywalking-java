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

import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeader;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.rocketMQ.v5.define.SendCallBackEnhanceInfo;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.rocketmq.common.message.MessageDecoder.NAME_VALUE_SEPARATOR;
import static org.apache.rocketmq.common.message.MessageDecoder.PROPERTY_SEPARATOR;

/**
 * {@link MessageSendInterceptor} create exit span when the method {@link org.apache.rocketmq.client.impl.MQClientAPIImpl#sendMessage(String,
 * String, Message, org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeader, long,
 * org.apache.rocketmq.client.impl.CommunicationMode, org.apache.rocketmq.client.producer.SendCallback,
 * org.apache.rocketmq.client.impl.producer.TopicPublishInfo, org.apache.rocketmq.client.impl.factory.MQClientInstance,
 * int, org.apache.rocketmq.client.hook.SendMessageContext, org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl)}
 * execute.
 *
 *
 * <pre>
 * 增强类：org.apache.rocketmq.client.impl.MQClientAPIImpl
 * 增强方法：SendResult sendMessage(
 *                  String addr, String brokerName, Message msg, SendMessageRequestHeader requestHeader,
 *                  long timeoutMillis, CommunicationMode communicationMode, SendCallback sendCallback,
 *                  TopicPublishInfo topicPublishInfo, MQClientInstance instance, int retryTimesWhenSendFailed,
 *                  SendMessageContext context, DefaultMQProducerImpl producer)
 * </pre>
 */
public class MessageSendInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String ASYNC_SEND_OPERATION_NAME_PREFIX = "RocketMQ/";

    private static final AbstractTag<String> MQ_MESSAGE_KEYS_TAG = Tags.ofKey("mq.message.keys");

    private static final AbstractTag<String> MQ_MESSAGE_TAGS_TAG = Tags.ofKey("mq.message.tags");

    /**
     *
     * @param objInst MQClientAPIImpl 的增强对象
     * @param method sendMessage 方法
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        // 取 第三个参数 Message msg
        Message message = (Message) allArguments[2];
        // 创建 跨进程传输载体
        ContextCarrier contextCarrier = new ContextCarrier();
        // 从 MQClientAPIImpl 增强对象 的 动态增强域 取值（namesrv）
        String namingServiceAddress = String.valueOf(objInst.getSkyWalkingDynamicField());

        // 创建 exit span，并将 当前的 TracerContext 和 该 exit span 的相关信息 注入 到 ContextCarrier 中
        AbstractSpan span = ContextManager.createExitSpan(buildOperationName(message.getTopic()), contextCarrier, namingServiceAddress);
        // 将 span 定义为 rocketMQ-producer 组件
        span.setComponent(ComponentsDefine.ROCKET_MQ_PRODUCER);
        // 取 第一个参数 String addr，并将其设置为 exit span 的 mq.broker 标签的值
        Tags.MQ_BROKER.set(span, (String) allArguments[0]);
        // 设置 exit span 的 mq.topic 标签的值
        Tags.MQ_TOPIC.set(span, message.getTopic());
        // 获取 Message 的 properties（扩展属性）中的 KEYS 对应的值
        String keys = message.getKeys();
        if (StringUtil.isNotBlank(keys)) {
            // 设置 exit span 的 mq.message.keys 标签的值
            span.tag(MQ_MESSAGE_KEYS_TAG, keys);
        }
        // 获取 Message 的 properties（扩展属性）中的 TAGS 对应的值
        String tags = message.getTags();
        if (StringUtil.isNotBlank(tags)) {
            // 设置 exit span 的 mq.message.tags 标签的值
            span.tag(MQ_MESSAGE_TAGS_TAG, tags);
        }

        // 将 当前时间 注入到 contextCarrier 的 ExtensionContext
        contextCarrier.extensionInjector().injectSendingTimestamp();
        // 将 exit span 的 Layer 设置为 MQ
        SpanLayer.asMQ(span);

        // 取 第四个参数 SendMessageRequestHeader requestHeader
        SendMessageRequestHeader requestHeader = (SendMessageRequestHeader) allArguments[3];
        StringBuilder properties = new StringBuilder(requestHeader.getProperties());
        // 将 上游 的 链路信息（上下文载体的 key-value） 放入到 requestHeader.properties 中传递给 下游
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (!StringUtil.isEmpty(next.getHeadValue())) {
                if (properties.length() > 0 && properties.charAt(properties.length() - 1) != PROPERTY_SEPARATOR) {
                    // adapt for RocketMQ 4.9.x or later
                    properties.append(PROPERTY_SEPARATOR);
                }
                properties.append(next.getHeadKey());
                properties.append(NAME_VALUE_SEPARATOR);
                properties.append(next.getHeadValue());
            }
        }
        // 重置 requestHeader.properties
        requestHeader.setProperties(properties.toString());

        // 取 第七个参数 SendCallback sendCallback
        if (allArguments[6] != null) {
            // 将 第七个参数 SendCallback 增强对象 的 动态增强域 的值设置为 新创建的 SendCallBackEnhanceInfo 对象
            ((EnhancedInstance) allArguments[6]).setSkyWalkingDynamicField(new SendCallBackEnhanceInfo(message.getTopic(), ContextManager
                .capture()));
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        // 结束 active span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }

    private String buildOperationName(String topicName) {
        return ASYNC_SEND_OPERATION_NAME_PREFIX + topicName + "/Producer";
    }
}
