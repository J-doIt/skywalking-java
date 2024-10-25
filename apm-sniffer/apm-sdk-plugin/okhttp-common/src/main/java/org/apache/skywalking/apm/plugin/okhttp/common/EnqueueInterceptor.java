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

package org.apache.skywalking.apm.plugin.okhttp.common;

import okhttp3.Request;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * {@link EnqueueInterceptor} create a local span and the prefix of the span operation name is start with `Async` when
 * the `enqueue` method called and also put the `ContextSnapshot` and `RealCall` instance into the
 * `SkyWalkingDynamicField`.
 *
 * <pre>
 * 增强类 RealCall
 * 增强构造函数：RealCall ( OkHttpClient, Request, forWebSocket:Boolean )，
 *      拦截器：RealCallInterceptor
 * 增强方法：一个参数的 fun enqueue(responseCallback: Callback)，
 *      拦截器：EnqueueInterceptor
 * </pre>
 */
public class EnqueueInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {
    /**
     *
     * @param objInst RealCall
     * @param method fun enqueue(responseCallback: Callback)，
     * @param allArguments Callback 对象
     * @param argumentsTypes Callback.class
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        // enqueue 方法的第一个参数 被增强过了（CallbackInstrumentation 中声明了要增强 okhttp3.Callback 及其子类）
        EnhancedInstance callbackInstance = (EnhancedInstance) allArguments[0];
        Request request = (Request) objInst.getSkyWalkingDynamicField();
        // 创建 操作名 为 Async+uri 的 local span
        ContextManager.createLocalSpan("Async" + request.url().uri().getPath());

        /**
         * Here is the process about how to trace the async function.
         *
         * 1. Storage `Request` object into `RealCall` instance when the constructor of `RealCall` called.
         * 2. Put the `RealCall` instance to `CallBack` instance
         * 3. Get the `RealCall` instance from `CallBack` and then Put the `RealCall` into `AsyncCall` instance
         *    since the constructor of `RealCall` called.
         * 5. Create the exit span by using the `RealCall` instance when `AsyncCall` method called.
         *
         * <pre>
         * (QFTODO：以下是有关如何跟踪 async 函数的过程。
         *      1. 当 'RealCall' 的构造函数调用时，将 'Request' 对象存储到 'RealCall' 实例中。
         *      2. 将 RealCall 实例放入 CallBack 实例
         *      3. 从 CallBack 中获取 RealCall 实例，然后将 RealCall 放入 AsyncCall 实例中，因为 RealCall 的构造函数调用了该实例。
         *      5. 调用 'AsyncCall' 方法时，使用 'RealCall' 实例创建退出 span。
         * )
         * </pre>
         */

        // 为 Callback 的 动态增强字段 赋值为 EnhanceRequiredInfo，并捕获快照
        callbackInstance.setSkyWalkingDynamicField(new EnhanceRequiredInfo(objInst, ContextManager.capture()));
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        // stop span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 Throwable
        ContextManager.activeSpan().log(t);
    }

    /**
     * 增强构造函数 RealCall ( OkHttpClient, Request, forWebSocket:Boolean )
     * @param objInst RealCall
     * @param allArguments  OkHttpClient, Request, forWebSocket:Boolean
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        // 将 Request 对象复制给 动态增强的字段 QFTODO: RealCallInterceptor.onConstruct 不是已经实现过了吗？
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }
}
