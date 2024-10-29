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

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;

/**
 * HierarchyMatchExceptionCheckStrategy does a hierarchy check for a traced exception. If it or its parent has been
 * listed in org.apache.skywalking.apm.agent.core.conf.Config.StatusCheck#IGNORED_EXCEPTIONS, the error status of the
 * span wouldn't be changed.
 * <pre>
 * (HierarchyMatchExceptionCheckStrategy 对跟踪的异常进行层次检查。
 * 如果它或它的父级已在 {@link org.apache.skywalking.apm.agent.core.conf.Config.StatusCheck#IGNORED_EXCEPTIONS} ，不会改变 span 的错误状态。)
 * </pre>
 */
public class HierarchyMatchExceptionCheckStrategy implements ExceptionCheckStrategy {

    @Override
    public boolean isError(final Throwable e) {
        Class<? extends Throwable> clazz = e.getClass();
        StatusCheckService statusTriggerService = ServiceManager.INSTANCE.findService(StatusCheckService.class);
        String[] ignoredExceptionNames = statusTriggerService.getIgnoredExceptionNames();
        for (final String ignoredExceptionName : ignoredExceptionNames) {
            try {
                // 根据异常名称加载对应的类
                Class<?> parentClazz = Class.forName(ignoredExceptionName, true, clazz.getClassLoader());
                // 检查 当前异常类 是否是被忽略的异常类的子类或实现类
                if (parentClazz.isAssignableFrom(clazz)) {
                    // 如果是，则认为这不是一个异常。
                    return false;
                }
            } catch (ClassNotFoundException ignore) {
            }
        }
        // 如果没有匹配到任何被忽略的异常类，则认为这是一个异常
        return true;
    }
}
