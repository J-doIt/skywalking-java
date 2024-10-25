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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.loader.InterceptorInstanceLoader;

/**
 * The actual byte-buddy's interceptor to intercept class instance methods. In this class, it provides a bridge between
 * byte-buddy and sky-walking plugin.
 */
public class InstMethodsInterV2 {
    private static final ILog LOGGER = LogManager.getLogger(InstMethodsInterV2.class);

    private InstanceMethodsAroundInterceptorV2 interceptor;

    public InstMethodsInterV2(String instanceMethodsAroundInterceptorClassName, ClassLoader classLoader) {
        try {
            interceptor = InterceptorInstanceLoader.load(instanceMethodsAroundInterceptorClassName, classLoader);
        } catch (Throwable t) {
            throw new PluginException("Can't create InstanceMethodsAroundInterceptor.", t);
        }
    }

    /**
     * 拦截器方法，用于在运行时动态地拦截目标对象的方法调用。
     *
     * @param obj          被拦截的对象实例，类型为{@link EnhancedInstance}，表示这是一个增强的对象，可能包含额外的监控或追踪信息。
     * @param allArguments 调用目标方法时传递的所有参数组成的数组。
     * @param zuper        代表被拦截方法的原始调用的{@link Callable}对象，可以通过它来调用原始方法。
     * @param method       被拦截的方法的反射对象，提供了访问方法名、返回类型等元数据的能力。
     * @return 方法执行的结果。如果拦截逻辑决定不继续执行原始方法，则直接返回上下文中的结果；否则，返回原始方法的调用结果。
     * @throws Throwable 如果在方法调用过程中或拦截器逻辑中发生异常，将向上抛出。
     */
    @RuntimeType
    public Object intercept(@This Object obj, @AllArguments Object[] allArguments, @SuperCall Callable<?> zuper,
                            @Origin Method method) throws Throwable {
        EnhancedInstance targetObject = (EnhancedInstance) obj;

        // 创建方法调用上下文，用于存储调用过程中的附加信息
        MethodInvocationContext context = new MethodInvocationContext();
        try {
            // 在目标方法执行前调用拦截器的前置处理逻辑
            interceptor.beforeMethod(targetObject, method, allArguments, method.getParameterTypes(), context);
        } catch (Throwable t) {
            LOGGER.error(t, "class[{}] before method[{}] intercept failure", obj.getClass(), method.getName());
        }

        Object ret = null;
        try {
            // 根据上下文是否继续执行，决定是直接返回上下文中的结果还是调用原始方法
            if (!context.isContinue()) {
                ret = context._ret();
            } else {
                // 调用原始方法
                ret = zuper.call();
            }
        } catch (Throwable t) {
            try {
                // 尝试调用异常处理逻辑
                interceptor.handleMethodException(targetObject, method, allArguments, method.getParameterTypes(), t, context);
            } catch (Throwable t2) {
                LOGGER.error(t2, "class[{}] handle method[{}] exception failure", obj.getClass(), method.getName());
            }
            // 再次抛出原始异常，确保异常能被上层正确感知
            throw t;
        } finally {
            try {
                // 调用后置处理逻辑，后置处理可能会修改返回值
                ret = interceptor.afterMethod(targetObject, method, allArguments, method.getParameterTypes(), ret, context);
            } catch (Throwable t) {
                LOGGER.error(t, "class[{}] after method[{}] intercept failure", obj.getClass(), method.getName());
            }
        }
        return ret;
    }
}
