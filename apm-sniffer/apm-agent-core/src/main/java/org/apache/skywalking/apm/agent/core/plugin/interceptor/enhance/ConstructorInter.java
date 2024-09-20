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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;

/**
 * The actual byte-buddy's interceptor to intercept constructor methods. In this class, it provides a bridge between
 * byte-buddy and sky-walking plugin.
 *
 * <pre>
 * (实际的 byte-buddy 的拦截器 拦截类 构造方法。在这个类中，它提供了 byte-buddy 和 sky-walking plugin 之间的桥梁。)
 *
 * 构造方法 Inter
 * </pre>
 */
public class ConstructorInter {
    private static final ILog LOGGER = LogManager.getLogger(ConstructorInter.class);

    /**
     * An {@link InstanceConstructorInterceptor} This name should only stay in {@link String}, the real {@link Class}
     * type will trigger classloader failure. If you want to know more, please check on books about Classloader or
     * Classloader appointment mechanism.
     *
     * <pre>
     * (这个名字应该只保留在String中，真正的Class类型会触发类加载器失败。
     * 如果您想了解更多，请查阅有关Classloader或Classloader委托机制的书籍。)
     * 构造方法拦截器
     * </pre>
     */
    private InstanceConstructorInterceptor interceptor;

    /**
     * @param constructorInterceptorClassName class full name.
     */
    public ConstructorInter(String constructorInterceptorClassName, ClassLoader classLoader) throws PluginException {
        try {
            // 加载构造方法拦截器
            interceptor = InterceptorInstanceLoader.load(constructorInterceptorClassName, classLoader);
        } catch (Throwable t) {
            throw new PluginException("Can't create InstanceConstructorInterceptorV2.", t);
        }
    }

    /**
     * Intercept the target constructor.
     * <pre>
     * (拦截目标构造函数。)
     * 在构造方法执行完成后进行拦截。
     * </pre>
     *
     * @param obj          target class instance.
     * @param allArguments all constructor arguments
     */
    @RuntimeType
    public void intercept(@This Object obj, @AllArguments Object[] allArguments) {
        try {
            EnhancedInstance targetObject = (EnhancedInstance) obj;

            // interceptor.onConstruct
            interceptor.onConstruct(targetObject, allArguments);
        } catch (Throwable t) {
            LOGGER.error("ConstructorInter failure.", t);
        }

    }
}
