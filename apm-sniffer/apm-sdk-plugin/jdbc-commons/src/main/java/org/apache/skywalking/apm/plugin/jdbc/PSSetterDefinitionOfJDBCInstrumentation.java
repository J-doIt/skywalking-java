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

package org.apache.skywalking.apm.plugin.jdbc;

import java.util.Set;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.plugin.jdbc.define.Constants;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static org.apache.skywalking.apm.plugin.jdbc.define.Constants.PS_IGNORABLE_SETTERS;
import static org.apache.skywalking.apm.plugin.jdbc.define.Constants.PS_SETTERS;

/**
 * <pre>
 * PreparedStatement 实现类的 Setter实例方法 匹配器（可忽略|不可忽略 的 setter()）
 *
 * 增强方法：
 *          {@link org.apache.skywalking.apm.plugin.jdbc.define.Constants#PS_IGNORABLE_SETTERS}
 *          or
 *          {@link org.apache.skywalking.apm.plugin.jdbc.define.Constants#PS_SETTERS}
 *      拦截器：
 *          {@link org.apache.skywalking.apm.plugin.jdbc.define.Constants#PREPARED_STATEMENT_IGNORABLE_SETTER_METHODS_INTERCEPTOR}
 *          or
 *          {@link org.apache.skywalking.apm.plugin.jdbc.define.Constants#PREPARED_STATEMENT_SETTER_METHODS_INTERCEPTOR}
 * </pre>
 */
public class PSSetterDefinitionOfJDBCInstrumentation implements InstanceMethodsInterceptPoint {
    /** 是否可忽略 */
    private final boolean ignorable;

    public PSSetterDefinitionOfJDBCInstrumentation(boolean ignorable) {
        this.ignorable = ignorable;
    }

    /** 定义方法匹配器 */
    @Override
    public ElementMatcher<MethodDescription> getMethodsMatcher() {
        // 初始化一个空的匹配器
        ElementMatcher.Junction<MethodDescription> matcher = none();

        // remove TRACE_SQL_PARAMETERS judgement for dynamic config
        // 根据 ignorable 的值选择不同的 setter 方法集合
        final Set<String> setters = ignorable ? PS_IGNORABLE_SETTERS : PS_SETTERS;
        // 遍历所有的 setter 方法，并构建匹配器
        for (String setter : setters) {
            // 匹配指定名称且为公共方法的 setter 方法
            matcher = matcher.or(named(setter).and(isPublic()));
        }

        // 返回最终的匹配器
        return matcher;
    }

    /** 获取拦截器类名 */
    @Override
    public String getMethodsInterceptor() {
        // 根据 ignorable 的值返回不同的拦截器类名
        return ignorable ? Constants.PREPARED_STATEMENT_IGNORABLE_SETTER_METHODS_INTERCEPTOR : Constants.PREPARED_STATEMENT_SETTER_METHODS_INTERCEPTOR;
    }

    @Override
    public boolean isOverrideArgs() {
        // 不覆盖参数
        return false;
    }
}
