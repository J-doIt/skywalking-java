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

package org.apache.skywalking.apm.plugin.mybatis.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.ClassInstanceMethodsEnhancePluginDefineV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.v2.InstanceMethodsInterceptV2Point;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.mybatis.MyBatisMethodMatch;

import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * <pre>
 * 增强类：org.apache.ibatis.session.defaults.DefaultSqlSession
 * 增强方法：
 *          ≤T> T selectOne(String statement, ...)
 *          ≤K, V> Map≤K, V> selectMap(String statement, ...)
 *          int insert(String statement, ...)
 *          int delete(String statement, ...)
 *          void select(String statement, ResultHandler handler)
 *          void select(String statement, Object parameter, ResultHandler handler)
 *          ≤E> List≤E> selectList(String statement)
 *          ≤E> List≤E> selectList(String statement, Object parameter)
 *          int update(String statement)
 *      拦截器：org.apache.skywalking.apm.plugin.mybatis.MyBatisShellMethodInterceptor
 * </pre>
 */
public class MyBatisShellMethodInstrumentation extends ClassInstanceMethodsEnhancePluginDefineV2 {

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptV2Point[] getInstanceMethodsInterceptV2Points() {
        return new InstanceMethodsInterceptV2Point[] {
            new InstanceMethodsInterceptV2Point() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return MyBatisMethodMatch.INSTANCE.getMyBatisShellMethodMatcher();
                }

                @Override
                public String getMethodsInterceptorV2() {
                    return "org.apache.skywalking.apm.plugin.mybatis.MyBatisShellMethodInterceptor";
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    public ClassMatch enhanceClass() {
        return byName("org.apache.ibatis.session.defaults.DefaultSqlSession");
    }
}
