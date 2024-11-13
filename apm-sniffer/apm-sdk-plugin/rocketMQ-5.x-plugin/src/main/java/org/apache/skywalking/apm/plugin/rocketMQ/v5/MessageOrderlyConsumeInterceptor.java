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

import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

/**
 * {@link MessageOrderlyConsumeInterceptor} set the process status after the {@link
 * org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly#consumeMessage(java.util.List,
 * org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext)} method execute.
 *
 * <pre>
 * 增强类：org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly 的子类或实现类
 * 增强方法：ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context)
 * </pre>
 */
public class MessageOrderlyConsumeInterceptor extends AbstractMessageConsumeInterceptor {

    /**
     * @param objInst
     * @param method consumeMessage
     * @param allArguments
     * @param argumentsTypes [List<MessageExt>, ConsumeOrderlyContext]
     * @param ret ConsumeOrderlyStatus 对象
     * @return
     * @throws Throwable
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {

        ConsumeOrderlyStatus status = (ConsumeOrderlyStatus) ret;
        // 如果“顺序消费状态”为“暂时挂起当前队列”
        if (status == ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT) {
            AbstractSpan activeSpan = ContextManager.activeSpan();
            // 设置 active span 的 errorOccurred 标志位为 true
            activeSpan.errorOccurred();
            // 设置 active span 的 MQ状态标签
            Tags.MQ_STATUS.set(activeSpan, status.name());
        }
        // 停止 active span
        ContextManager.stopSpan();
        return ret;
    }

}
