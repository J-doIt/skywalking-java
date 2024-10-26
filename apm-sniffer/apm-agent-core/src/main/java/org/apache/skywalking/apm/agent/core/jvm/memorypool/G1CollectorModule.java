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
import java.util.List;

public class G1CollectorModule extends MemoryPoolModule {
    public G1CollectorModule(List<MemoryPoolMXBean> beans) {
        super(beans);
    }

    @Override
    protected String[] getPermNames() {
        // G1 GC 的 持久代（永久代） 名（jdk7及之前的）
        return new String[] {
            "G1 Perm Gen",
            "Compressed Class Space"
        };
    }

    @Override
    protected String[] getCodeCacheNames() {
        // G1 GC 的 缓存区 名
        return new String[] {"Code Cache"};
    }

    @Override
    protected String[] getEdenNames() {
        // G1 GC 的 年轻代 中 新生代 的 名
        return new String[] {"G1 Eden Space"};
    }

    @Override
    protected String[] getOldNames() {
        // G1 GC 的 老年代 名
        return new String[] {"G1 Old Gen"};
    }

    @Override
    protected String[] getSurvivorNames() {
        // G1 GC 的 年轻代 中  S1/S2 的名
        return new String[] {"G1 Survivor Space"};
    }

    @Override
    protected String[] getMetaspaceNames() {
        // G1 GC 的 元数据区 名
        return new String[] {"Metaspace"};
    }
}
