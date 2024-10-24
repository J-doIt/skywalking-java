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

package org.apache.skywalking.apm.agent.core.context;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.agent.core.conf.RuntimeContextConfiguration;

/**
 * RuntimeContext is alive during the tracing context. It will not be serialized to the collector, and always stays in
 * the same context only.
 * <p>
 * In most cases, it means it only stays in a single thread for context propagation.
 *
 * <pre>
 * (RuntimeContext 在追踪上下文期间是活跃的。它不会被序列化到 collector，并且始终保留在相同的上下文中。
 *  在大多数情况下，这意味着它只在单个线程中用于上下文传播。)
 * </pre>
 *
 */
public class RuntimeContext {
    /** 当前线程 的 RuntimeContext */
    private final ThreadLocal<RuntimeContext> contextThreadLocal;
    /** */
    private Map<Object, Object> context = new ConcurrentHashMap<>(0);

    public RuntimeContext(ThreadLocal<RuntimeContext> contextThreadLocal) {
        this.contextThreadLocal = contextThreadLocal;
    }

    public void put(Object key, Object value) {
        context.put(key, value);
    }

    public Object get(Object key) {
        return context.get(key);
    }

    /**
     * 从context中获取指定键对应的值，并转换为指定类型
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        return (T) context.get(key);
    }

    public void remove(Object key) {
        context.remove(key);

        // 如果 context 为空，则从 contextThreadLocal 中移除 当前RuntimeContext
        if (context.isEmpty()) {
            contextThreadLocal.remove();
        }
    }

    /**
     * 捕获当前RuntimeContext的状态并返回一个快照
     */
    public RuntimeContextSnapshot capture() {
        Map<Object, Object> runtimeContextMap = new HashMap<>();
        // 遍历需要传播的上下文键
        for (String key : RuntimeContextConfiguration.NEED_PROPAGATE_CONTEXT_KEY) {
            Object value = this.get(key);
            if (value != null) {
                runtimeContextMap.put(key, value);
            }
        }

        return new RuntimeContextSnapshot(runtimeContextMap);
    }

    /**
     * 接受一个RuntimeContextSnapshot，并将其中的键值对应用到当前RuntimeContext
     */
    public void accept(RuntimeContextSnapshot snapshot) {
        // 获取snapshot中的所有键值对
        Iterator<Map.Entry<Object, Object>> iterator = snapshot.iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> runtimeContextItem = iterator.next();
            // 将键值对添加到当前RuntimeContext
            ContextManager.getRuntimeContext().put(runtimeContextItem.getKey(), runtimeContextItem.getValue());
        }
    }
}
