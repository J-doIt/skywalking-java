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

package org.apache.skywalking.apm.agent.core.jvm.memorypool;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.MemoryPool;
import org.apache.skywalking.apm.network.language.agent.v3.PoolType;

/**
 * MemoryPool 指标访问器抽象类。
 * 不同 GC 算法之间，内存区域命名不同，通过如下六个方法抽象，分别对应不同内存区域，形成映射关系，屏蔽差异：
 */
public abstract class MemoryPoolModule implements MemoryPoolMetricsAccessor {
    /** java.lang.management.MemoryPoolMXBean */
    private List<MemoryPoolMXBean> beans;

    public MemoryPoolModule(List<MemoryPoolMXBean> beans) {
        this.beans = beans;
    }

    @Override
    public List<MemoryPool> getMemoryPoolMetricsList() {
        List<MemoryPool> poolList = new LinkedList<MemoryPool>();
        // 循环每个内存区域，收集每个 MemoryPool 指标
        for (MemoryPoolMXBean bean : beans) {
            String name = bean.getName();
            // 逐个内存区域名字判断，获得对应的内存区域类型。
            PoolType type;
            if (contains(getCodeCacheNames(), name)) {
                type = PoolType.CODE_CACHE_USAGE;
            } else if (contains(getEdenNames(), name)) {
                type = PoolType.NEWGEN_USAGE;
            } else if (contains(getOldNames(), name)) {
                type = PoolType.OLDGEN_USAGE;
            } else if (contains(getSurvivorNames(), name)) {
                type = PoolType.SURVIVOR_USAGE;
            } else if (contains(getMetaspaceNames(), name)) {
                type = PoolType.METASPACE_USAGE;
            } else if (contains(getPermNames(), name)) {
                type = PoolType.PERMGEN_USAGE;
            } else {
                continue;
            }

            // 创建 MemoryUsage 对象，并添加到结果数组。
            MemoryUsage usage = bean.getUsage();
            poolList.add(MemoryPool.newBuilder()
                                   .setType(type)
                                   .setInit(usage.getInit())
                                   .setMax(usage.getMax())
                                   .setCommitted(usage.getCommitted())
                                   .setUsed(usage.getUsed())
                                   .build());
        }
        return poolList;
    }

    private boolean contains(String[] possibleNames, String name) {
        for (String possibleName : possibleNames) {
            if (name.equals(possibleName)) {
                return true;
            }
        }
        return false;
    }

    protected abstract String[] getPermNames();

    protected abstract String[] getCodeCacheNames();

    protected abstract String[] getEdenNames();

    protected abstract String[] getOldNames();

    protected abstract String[] getSurvivorNames();

    protected abstract String[] getMetaspaceNames();
}
