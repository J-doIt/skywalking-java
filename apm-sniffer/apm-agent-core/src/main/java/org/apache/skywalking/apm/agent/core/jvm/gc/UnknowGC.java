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

package org.apache.skywalking.apm.agent.core.jvm.gc;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.network.language.agent.v3.GC;
import org.apache.skywalking.apm.network.language.agent.v3.GCPhase;

/**
 * 未知的 GC 指标访问器实现类。
 * 每次 #getGCList() 方法，返回 GC 指标数组，但是每个指标元素是无具体数据的。
 */
public class UnknowGC implements GCMetricAccessor {
    @Override
    public List<GC> getGCList() {
        List<GC> gcList = new LinkedList<GC>();
        gcList.add(GC.newBuilder().setPhase(GCPhase.NEW).build());
        gcList.add(GC.newBuilder().setPhase(GCPhase.OLD).build());
        return gcList;
    }
}
