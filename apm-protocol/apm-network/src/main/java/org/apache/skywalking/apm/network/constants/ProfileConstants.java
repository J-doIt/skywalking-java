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

package org.apache.skywalking.apm.network.constants;

/**
 * profile task limit constants
 */
public class ProfileConstants {

    /**
     * Monitor duration must greater than 1 minutes
     * <pre>
     * （监视器持续时间必须大于 1 分钟）
     * </pre>
     */
    public static final int TASK_DURATION_MIN_MINUTE = 1;

    /**
     * The duration of the monitoring task cannot be greater than 15 minutes
     * <pre>
     * （监控任务的时长不能超过 15 分钟）
     * </pre>
     */
    public static final int TASK_DURATION_MAX_MINUTE = 15;

    /**
     * Dump period must be greater than or equals 10 milliseconds
     * <pre>
     * (转储周期必须 ≥ 10 毫秒)
     * </pre>
     */
    public static final int TASK_DUMP_PERIOD_MIN_MILLIS = 10;

    /**
     * Max sampling count must less than 10
     * <pre>
     * (最大采样计数必须 < 10)
     * </pre>
     */
    public static final int TASK_MAX_SAMPLING_COUNT = 10;

}
