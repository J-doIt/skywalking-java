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

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.rocketMQ.v5.define.ConsumerEnhanceInfos;

/**
 * <pre>
 * 增强类：org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
 * 增强方法：
 *          void registerMessageListener(MessageListener messageListener)
 *          void registerMessageListener(MessageListenerConcurrently messageListener)
 *          void registerMessageListener(MessageListenerOrderly messageListener)
 *          （MessageListener 是 MessageListenerConcurrently 和 MessageListenerOrderly 的父类）
 * </pre>
 */
public class RegisterMessageListenerInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * @param objInst
     * @param method registerMessageListener
     * @param allArguments
     * @param argumentsTypes [MessageListenerConcurrently] 或 [MessageListenerOrderly]
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        DefaultMQPushConsumer defaultMQPushConsumer = (DefaultMQPushConsumer) objInst;
        String namesrvAddr = defaultMQPushConsumer.getNamesrvAddr();
        // 创建 动态增强域 对象
        ConsumerEnhanceInfos consumerEnhanceInfos = new ConsumerEnhanceInfos(namesrvAddr);

        // 如果 第一个参数 messageListener 是被增强过的
        if (allArguments[0] instanceof EnhancedInstance) {
            EnhancedInstance enhancedMessageListener = (EnhancedInstance) allArguments[0];
            // 将 增强的messageListener 的 动态增强域 的值 设置为 consumerEnhanceInfos
            enhancedMessageListener.setSkyWalkingDynamicField(consumerEnhanceInfos);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        
    }
}
