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

package org.apache.skywalking.apm.toolkit.activation.trace;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.toolkit.activation.ToolkitPluginConfig;
import org.apache.skywalking.apm.toolkit.activation.util.TagUtil;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;
import org.apache.skywalking.apm.toolkit.trace.Trace;

import java.lang.reflect.Method;
import java.util.Map;

public class BaseTraceAnnotationInterceptor {

    /**
     * 创建 LocalSpan 对象
     */
    void beforeMethod(Method method, Object[] allArguments) {
        // 获取方法上的 @Trace
        Trace trace = method.getAnnotation(Trace.class);
        // 获取操作名
        String operationName = trace.operationName();
        if (operationName.length() == 0 || ToolkitPluginConfig.Plugin.Toolkit.USE_QUALIFIED_NAME_AS_OPERATION_NAME) {
            // 如果操作名的值为空字符串。操作名将设置为类名+方法名
            operationName = MethodUtil.generateOperationName(method);
        }
        // 创建一个 LocalSpan
        final AbstractSpan localSpan = ContextManager.createLocalSpan(operationName);

        // QFTODO:
        final Map<String, Object> context = CustomizeExpression.evaluationContext(allArguments);

        // 获取方法上的 @Tags
        final org.apache.skywalking.apm.toolkit.trace.Tags tags = method.getAnnotation(Tags.class);
        if (tags != null && tags.value().length > 0) {
            for (final Tag tag : tags.value()) {
                // 该 tag 不是 returnedObj 返回值的标记
                if (!TagUtil.isReturnTag(tag.value())) {
                    // 给 span 设置 tag
                    TagUtil.tagSpan(localSpan, context, tag);
                }
            }
        }
        // 获取方法上的 @Tag
        final Tag tag = method.getAnnotation(Tag.class);
        if (tag != null && !TagUtil.isReturnTag(tag.value())) {
            // 给 span 设置 tag
            TagUtil.tagSpan(localSpan, context, tag);
        }
    }

    /**
     * 完成 LocalSpan 对象
     */
    void afterMethod(Method method, Object ret) {
        try {
            if (ret == null) {
                return;
            }
            // 获取 active 的 span
            final AbstractSpan localSpan = ContextManager.activeSpan();
            // 将 ret 存入 context 的 “returnedObj” 中
            final Map<String, Object> context = CustomizeExpression.evaluationReturnContext(ret);
            final Tags tags = method.getAnnotation(Tags.class);
            if (tags != null && tags.value().length > 0) {
                for (final Tag tag : tags.value()) {
                    // 该 tag 是 returnedObj 返回值的标记
                    if (TagUtil.isReturnTag(tag.value())) {
                        // 给 span 设置 tag
                        TagUtil.tagSpan(localSpan, context, tag);
                    }
                }
            }
            final Tag tag = method.getAnnotation(Tag.class);
            // 该 tag 是 returnedObj 返回值的标记
            if (tag != null && TagUtil.isReturnTag(tag.value())) {
                // 给 span 设置 tag
                TagUtil.tagSpan(localSpan, context, tag);
            }
        } finally {
            // 完成 LocalSpan 对象
            ContextManager.stopSpan();
        }
    }

    /**
     * 发生异常时，打印错误日志
     */
    void handleMethodException(Throwable t) {
        // 如果还有 active 的 span
        if (ContextManager.isActive()) {
            // 将异常的信息存入此Span
            ContextManager.activeSpan().log(t);
        }
    }
}
