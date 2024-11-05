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

package org.apache.skywalking.apm.plugin.rocketMQ.client.java.v5;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.rocketMQ.client.java.v5.define.ConsumerEnhanceInfos;

/**
 * {@link SimpleConsumerImplInterceptor} create local span when the method {@link org.apache.rocketmq.client.java.impl.consumer.SimpleConsumerImpl#receive(int,
 * java.time.Duration)} execute.
 *
 * <pre>
 * 增强类：org.apache.rocketmq.client.java.impl.consumer.SimpleConsumerImpl
 * 增强构造函数：SimpleConsumerImpl(ClientConfiguration clientConfiguration, String consumerGroup,
 *                  Duration awaitDuration, Map<String, FilterExpression> subscriptionExpressions)
 * 增强方法：List<MessageView> receive(int maxMessageNum, Duration invisibleDuration)
 * </pre>
 */
public class SimpleConsumerImplInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {
    public static final String CONSUMER_OPERATION_NAME_PREFIX = "RocketMQ/";

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                                   Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        List<MessageView> list = (List<MessageView>) ret;
        if (list.isEmpty()) {
            return ret;
        }
        String topics = list.stream().map(MessageView::getTopic).distinct().collect(Collectors.joining(","));
        // 创建 entry span ，参数 contextCarrier 传 null
        AbstractSpan span = ContextManager.createEntrySpan(CONSUMER_OPERATION_NAME_PREFIX + topics
                                                               + "/Consumer", null);
        SpanLayer.asMQ(span);
        String namesrvAddr = "";
        // 从 SimpleConsumerImpl 增强对象 的 动态增强域 取值（namesrv）
        Object skyWalkingDynamicField = objInst.getSkyWalkingDynamicField();
        if (skyWalkingDynamicField != null) {
            ConsumerEnhanceInfos consumerEnhanceInfos = (ConsumerEnhanceInfos) objInst.getSkyWalkingDynamicField();
            namesrvAddr = consumerEnhanceInfos.getNamesrvAddr();
        }
        Tags.MQ_TOPIC.set(span, topics);
        Tags.MQ_BROKER.set(span, namesrvAddr);
        span.setPeer(namesrvAddr);
        span.setComponent(ComponentsDefine.ROCKET_MQ_CONSUMER);

        for (MessageView messageView : list) {
            // 将上游的链路信息提取到新创建的carrier
            ContextCarrier contextCarrier = getContextCarrierFromMessage(messageView);
            // 从 contextCarrier 中 提取 追踪信息 到 当前tracingContext
            ContextManager.extract(contextCarrier);
        }

        // 结束 active span
        ContextManager.stopSpan();

        return ret;
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                            Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }

    @Override
    public void onConstruct(final EnhancedInstance objInst, final Object[] allArguments) throws Throwable {
        ClientConfiguration clientConfiguration = (ClientConfiguration) allArguments[0];
        String namesrvAddr = clientConfiguration.getEndpoints();

        // 创建 动态增强域 对象
        ConsumerEnhanceInfos consumerEnhanceInfos = new ConsumerEnhanceInfos(namesrvAddr);

        // 将 增强的SimpleConsumerImpl 的 动态增强域 的值 设置为 consumerEnhanceInfos
        objInst.setSkyWalkingDynamicField(consumerEnhanceInfos);
    }

    /**
     * 创建 ContextCarrier，并将 MessageView.Properties sw-key 对应的 value 设置到 该carrier。
     * （将上游的链路信息提取到新创建的carrier）
     */
    private ContextCarrier getContextCarrierFromMessage(MessageView message) {
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(message.getProperties().get(next.getHeadKey()));
        }

        return contextCarrier;
    }
}
