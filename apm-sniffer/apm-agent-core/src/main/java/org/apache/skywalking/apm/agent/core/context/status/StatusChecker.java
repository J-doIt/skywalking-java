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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.apache.skywalking.apm.agent.core.conf.Config;

@AllArgsConstructor
public enum StatusChecker {

    /**
     * All exceptions would make the span tagged as the error status.
     * （所有异常都会将 span 标记为错误状态。）
     */
    OFF(
        Collections.singletonList(new OffExceptionCheckStrategy()), // span的异常检查策略
        (isError, throwable) -> {
            // 忽略
        } // 检查到是异常后的回调
    ),

    /**
     * Hierarchy check the status of the traced exception.
     * <pre>
     * (层次结构检查跟踪的异常的状态。)
     * </pre>
     *
     * @see HierarchyMatchExceptionCheckStrategy
     * @see AnnotationMatchExceptionCheckStrategy
     */
    HIERARCHY_MATCH(
        Arrays.asList(
            new HierarchyMatchExceptionCheckStrategy(),
            new AnnotationMatchExceptionCheckStrategy()
        ), // span的异常检查策略
        (isError, throwable) -> {
            if (isError) {
                // 注册不需要忽略的异常
                ExceptionCheckContext.INSTANCE.registerErrorStatusException(throwable);
            } else {
                // 注册需要忽略的异常
                ExceptionCheckContext.INSTANCE.registerIgnoredException(throwable);
            }
        } // 检查到是异常后的回调
    );

    /** span的异常检查策略 */
    private final List<ExceptionCheckStrategy> strategies;

    /** 检查到是异常后的回调 */
    private final ExceptionCheckCallback callback;

    public boolean checkStatus(Throwable e) {
        int maxDepth = Config.StatusCheck.MAX_RECURSIVE_DEPTH;
        boolean isError = true;
        // 是异常 && e 不为空 && 没有超过 配置的异常时的最大递归深度
        while (isError && Objects.nonNull(e) && maxDepth-- > 0) {
            // 检查当前异常对象
            isError = check(e);
            // 更新异常对象为其原因（cause），以便在下一次迭代中检查更深层的原因
            e = e.getCause();
        }
        return isError;
    }

    private boolean check(final Throwable e) {
        boolean isError = ExceptionCheckContext.INSTANCE.isChecked(e)
            ? ExceptionCheckContext.INSTANCE.isError(e) // 如果异常已经在 ExceptionCheckContext 中被检查过，则直接从上下文中获取其是否为错误的状态
            : strategies.stream().allMatch(item -> item.isError(e)); // 否则，如果所有策略都认为是错误，则返回true
        // 检查到是异常后执行回调
        callback.onChecked(isError, e);
        return isError;
    }

    /**
     * The callback function would be triggered after an exception is checked by StatusChecker.
     * <pre>
     * (StatusChecker 检查异常后会触发回调函数。)
     * </pre>
     */
    @FunctionalInterface
    private interface ExceptionCheckCallback {
        void onChecked(Boolean isError, Throwable throwable);
    }

}
