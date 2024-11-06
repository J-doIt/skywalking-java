/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.skywalking.apm.plugin.jedis.v4;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * <pre>
 * 增强类：redis.clients.jedis.Pipeline
 * 增强方法：
 *          void sync()
 *          List≤Object> syncAndReturnAll()
 * </pre>
 *
 * <pre>
 * 增强类：redis.clients.jedis.Transaction
 * 增强方法：
 *          List≤Object> exec()
 *          String discard()
 * </pre>
 */
public class JedisMethodInterceptor implements InstanceMethodsAroundInterceptor {
    private static final StringTag TAG_ARGS = new StringTag("actual_target");

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 从 Pipeline、Transaction 增强对象 的 增强域 取值（ConnectionInformation）
        final ConnectionInformation connectionData = (ConnectionInformation) objInst.getSkyWalkingDynamicField();
        // Use cluster information to adapt Virtual Cache if exists, otherwise use real server host
        String peer =  StringUtil.isBlank(connectionData.getClusterNodes()) ? connectionData.getActualTarget() : connectionData.getClusterNodes();
        // 创建 exit span
        AbstractSpan span = ContextManager.createExitSpan("Jedis/" + method.getName(), peer);
        span.setComponent(ComponentsDefine.JEDIS);
        SpanLayer.asCache(span);
        Tags.CACHE_TYPE.set(span, "Redis");
        Tags.CACHE_CMD.set(span, "BATCH_EXECUTE");
        TAG_ARGS.set(span, connectionData.getActualTarget());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        // 结束 active span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        // 设置 active span 的 log 为 t
        span.log(t);
    }
}
