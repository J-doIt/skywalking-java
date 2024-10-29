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
 */

package org.apache.skywalking.apm.toolkit.activation.trace;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.status.AnnotationMatchExceptionCheckStrategy;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

public class IgnoredExceptionConstructInterceptor implements InstanceConstructorInterceptor {

    /**
     * Inject a tag field to mark the exception should be not thought error status.
     * <pre>
     * (注入 tag 字段来标记异常时，不应被视为错误状态。)
     * </pre>
     *
     * @param objInst 被注解了 IgnoredException 的异常类的增强类
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        if (ContextManager.isActive()) {
            // 将 AnnotationMatchExceptionCheckStrategy 字符串 赋值给 objInst 的动态字段
            objInst.setSkyWalkingDynamicField(AnnotationMatchExceptionCheckStrategy.class.getSimpleName());
        }
    }
}
