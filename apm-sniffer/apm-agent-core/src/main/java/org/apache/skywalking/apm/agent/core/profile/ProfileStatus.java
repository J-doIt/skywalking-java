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

package org.apache.skywalking.apm.agent.core.profile;

/**
 * Profile status, include entire profile cycle
 * <pre>
 * (分析状态，包括整个分析周期)
 * </pre>
 */
public enum ProfileStatus {
    /**
     * 无分析
     */
    NONE,

    /**
     * Prepare to profile, until {@link ProfileTask#getMinDurationThreshold()} reached,
     * Once the status changed to profiling, the thread snapshot is officially started
     * <pre>
     * (准备分析，直到 ProfileTask.getMinDurationThreshold() 达到，一旦状态变为 profiling，线程快照 就 正式启动)
     * </pre>
     */
    PENDING,

    /**
     * 配置操作已启动
     */
    PROFILING,

    /**
     * 当前的 TracingContext 已完成，或者 当前线程 不可用。
     */
    STOPPED
}
