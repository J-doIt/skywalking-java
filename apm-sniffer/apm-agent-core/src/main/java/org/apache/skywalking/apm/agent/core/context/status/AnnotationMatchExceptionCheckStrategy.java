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

package org.apache.skywalking.apm.agent.core.context.status;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

/**
 * AnnotationMatchExceptionCheckStrategy does an annotation matching check for a traced exception. If it has been
 * annotated with org.apache.skywalking.apm.toolkit.trace.IgnoredException, the error status of the span wouldn't be
 * changed. Because of the annotation supports integration, the subclasses would be also annotated with it.
 *
 * <pre>
 * (AnnotationMatchExceptionCheckStrategy 对 跟踪的异常 进行 注释匹配检查。
 * 如果它已经用了 @IgnoredException，则不会改变span的错误状态。
 * 因为注释支持集成，所以子类也将使用它进行注释。)
 * </pre>
 */
public class AnnotationMatchExceptionCheckStrategy implements ExceptionCheckStrategy {

    private static final String TAG_NAME = AnnotationMatchExceptionCheckStrategy.class.getSimpleName();

    @Override
    public boolean isError(final Throwable e) {
        // 如果 e 是被SW动态增强的，且 动态字段 是 AnnotationMatchExceptionCheckStrategy 字符串，则表示该 span 不是异常
        return !(e instanceof EnhancedInstance)
                || !TAG_NAME.equals(((EnhancedInstance) e).getSkyWalkingDynamicField());
    }
}