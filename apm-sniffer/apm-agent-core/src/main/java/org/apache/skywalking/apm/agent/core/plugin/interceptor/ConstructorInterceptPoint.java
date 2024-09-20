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

package org.apache.skywalking.apm.agent.core.plugin.interceptor;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Objects;

/**
 * One of the three "Intercept Point". "Intercept Point" is a definition about where and how intercept happens. In this
 * "Intercept Point", the definition targets class's constructors, and the interceptor.
 * <p>
 * ref to two others: {@link StaticMethodsInterceptPoint} and {@link InstanceMethodsInterceptPoint}
 * <p>
 */
public interface ConstructorInterceptPoint {
    /**
     * Constructor matcher
     * 构造函数匹配器
     *
     * @return matcher instance.
     */
    ElementMatcher<MethodDescription> getConstructorMatcher();

    /**
     * 构造方法拦截器类名
     *
     * @return represents a class name, the class instance must be a instance of {@link
     * org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor}
     */
    String getConstructorInterceptor();

    /**
     * To ensure that the hashCode for recreating the XxxInterceptPoint instance is the same as the previous instance,
     * each ElementMatcher implementation class needs to implement toString() method.
     *
     * <pre>
     * (为了确保用于重新创建XxxInterceptPoint实例的hashCode与前一个实例相同，每个ElementMatcher实现类都需要实现toString()方法。)
     * </pre>
     *
     * @return hashCode of this intercept point
     */
    default int computeHashCode() {
        return Objects.hash(this.getClass().getName(), this.getConstructorMatcher().toString(), this.getConstructorInterceptor());
    }
}
