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

package org.apache.skywalking.apm.agent.core.context.ids;

import java.util.UUID;

import org.apache.skywalking.apm.util.StringUtil;

/** 全局 ID 生成器 */
public final class GlobalIdGenerator {
    /** 应用程序实例ID，程序启动后一直不变 */
    private static final String PROCESS_ID = UUID.randomUUID().toString().replaceAll("-", "");
    /**
     * <pre>
     * 线程变量，为不同线程 在执行 this.generate() 时提供 ”时间戳 + nextSeq“
     *      时间戳：线程 执行 this.generate() 的时间
     *      nextSeq：同一线程中，调用 this.generate() 次数的累加（到达 10000 后则从0开始计数）
     * </pre>
     */
    private static final ThreadLocal<IDContext> THREAD_ID_SEQUENCE = ThreadLocal.withInitial(
        () -> new IDContext(System.currentTimeMillis(), (short) 0));

    private GlobalIdGenerator() {
    }

    /**
     * Generate a new id, combined by three parts.
     * <p>
     * The first one represents application instance id.
     * <p>
     * The second one represents thread id.
     * <p>
     * The third one also has two parts, 1) a timestamp, measured in milliseconds 2) a seq, in current thread, between
     * 0(included) and 9999(included)
     *
     * <pre>
     * (生成一个新 ID，由三个部分组合。
     *      第一个 ID 表示：应用程序实例 ID。
     *      第二个 ID 表示：线程 ID。
     *      第三个也包含两个部分，
     *          1） 时间戳，以毫秒为单位
     *          2） 一个 seq，在当前线程中，介于 0（包含）和 10000（包含）之间
     * 返回：
     * 用于表示跟踪或区段的唯一 ID)
     * </pre>
     *
     * @return unique id to represent a trace or segment
     */
    public static String generate() {
        return StringUtil.join(
            '.',
            PROCESS_ID, // 应用程序实例 ID
            String.valueOf(Thread.currentThread().getId()), // 当前线程 ID
            String.valueOf(THREAD_ID_SEQUENCE.get().nextSeq()) // 时间戳 * 1000 + threadSeq
        );
    }

    /** TraceId 上下文，
     * 在 线程变量 中存活. */
    private static class IDContext {
        /** 上次时间戳 */
        private long lastTimestamp;
        /** 介于 0（包含）和 10000（包含）之间，到达 10000 后则从0开始计数 */
        private short threadSeq;

        // Just for considering time-shift-back only.（仅用于考虑时间倒移。）
        /** 上次 发生时间倒移 的 时间戳 */
        private long lastShiftTimestamp;
        /** 发生时间倒移 的 计数器 */
        private int lastShiftValue;

        /**
         * 不同线程线程 第一次 执行 this.generate()  时被初始化
         * @param lastTimestamp 不同线程线程 第一次 执行 this.generate() 的时间
         * @param threadSeq 初始值为 0
         */
        private IDContext(long lastTimestamp, short threadSeq) {
            this.lastTimestamp = lastTimestamp;
            this.threadSeq = threadSeq;
        }

        private long nextSeq() {
            return timestamp() * 10000 + nextThreadSeq();
        }

        private long timestamp() {
            long currentTimeMillis = System.currentTimeMillis();

            // 如果 当前时间 < 上次时间戳
            if (currentTimeMillis < lastTimestamp) {
                // Just for considering time-shift-back by Ops or OS. @hanahmily 's suggestion.
                // 如果 当前时间 != 上次发生时间倒移时的时间戳
                if (lastShiftTimestamp != currentTimeMillis) {
                    lastShiftValue++;
                    lastShiftTimestamp = currentTimeMillis;
                }
                return lastShiftValue;
            } else {
                lastTimestamp = currentTimeMillis;
                return lastTimestamp;
            }
        }

        private short nextThreadSeq() {
            if (threadSeq == 10000) {
                threadSeq = 0;
            }
            return threadSeq++;
        }
    }
}
