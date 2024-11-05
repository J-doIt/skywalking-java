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

package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RedissonPluginConfig {
    public static class Plugin {
        @PluginConfig(root = RedissonPluginConfig.class)
        public static class Redisson {
            /**
             * If set to true, the parameters of the Redis command would be collected.
             * （如果设置为 true，则会收集 Redis 命令的参数。）
             */
            public static boolean TRACE_REDIS_PARAMETERS = false;
            /**
             * For the sake of performance, SkyWalking won't save Redis parameter string into the tag.
             * If TRACE_REDIS_PARAMETERS is set to true, the first {@code REDIS_PARAMETER_MAX_LENGTH} parameter
             * characters would be collected.
             * <p>
             * Set a negative number to save specified length of parameter string to the tag.
             *
             * <pre>
             * (为了性能，SkyWalking 不会将 Redis 参数字符串保存到 tag 中。如果 TRACE_REDIS_PARAMETERS 设置为 true，则将收集前 REDIS_PARAMETER_MAX_LENGTH 个参数字符。
             * 设置负数，将指定长度的参数字符串保存到标签中。)
             * </pre>
             */
            public static int REDIS_PARAMETER_MAX_LENGTH = 128;
            /**
             * If set to true, the PING command would be collected.
             * （如果设置为 true，则将收集 PING 命令。）
             */
            public static boolean SHOW_PING_COMMAND = false;
            /**
             * If set to true, the detail of the Redis batch commands would be collected.
             * (如果设置为 true，则将收集 Redis 批处理命令的详细信息。)
             */
            public static boolean SHOW_BATCH_COMMANDS = false;

            /**
             * Operation represent a cache span is "write" or "read" action , and "op"(operation) is tagged with key "cache.op" usually
             * This config term define which command should be converted to write Operation .
             *
             * <pre>
             * cache span 的 写操作
             * </pre>
             *
             * @see org.apache.skywalking.apm.agent.core.context.tag.Tags#CACHE_OP
             * @see RedisConnectionMethodInterceptor#parseOperation(String)
             */
            public static Set<String> OPERATION_MAPPING_WRITE = new HashSet<>(Arrays.asList(
                    "getset",
                    "set",
                    "setbit",
                    "setex ",
                    "setnx ",
                    "setrange",
                    "strlen ",
                    "mset",
                    "msetnx ",
                    "psetex",
                    "incr ",
                    "incrby ",
                    "incrbyfloat",
                    "decr ",
                    "decrby ",
                    "append ",
                    "hmset",
                    "hset",
                    "hsetnx ",
                    "hincrby",
                    "hincrbyfloat",
                    "hdel",
                    "rpoplpush",
                    "rpush",
                    "rpushx",
                    "lpush",
                    "lpushx",
                    "lrem",
                    "ltrim",
                    "lset",
                    "brpoplpush",
                    "linsert",
                    "sadd",
                    "sdiff",
                    "sdiffstore",
                    "sinterstore",
                    "sismember",
                    "srem",
                    "sunion",
                    "sunionstore",
                    "sinter",
                    "zadd",
                    "zincrby",
                    "zinterstore",
                    "zrange",
                    "zrangebylex",
                    "zrangebyscore",
                    "zrank",
                    "zrem",
                    "zremrangebylex",
                    "zremrangebyrank",
                    "zremrangebyscore",
                    "zrevrange",
                    "zrevrangebyscore",
                    "zrevrank",
                    "zunionstore",
                    "xadd",
                    "xdel",
                    "del",
                    "xtrim"
            ));
            /**
             * Operation represent a cache span is "write" or "read" action , and "op"(operation) is tagged with key "cache.op" usually
             * This config term define which command should be converted to write Operation .
             *
             * <pre>
             * (表示 cache span 的操作是 “write” 或 “read” action，而 “op”（operation） 则使用键 “cache.op“ 通常这个配置术语定义应该将哪个命令转换为 write Operation 。)
             *
             * cache span 的 读操作
             * </pre>
             *
             * @see org.apache.skywalking.apm.agent.core.context.tag.Tags#CACHE_OP
             * @see RedisConnectionMethodInterceptor#parseOperation(String)
             */
            public static Set<String> OPERATION_MAPPING_READ = new HashSet<>(Arrays.asList(
                    "getrange",
                    "getbit ",
                    "mget",
                    "hvals",
                    "hkeys",
                    "hlen",
                    "hexists",
                    "hget",
                    "hgetall",
                    "hmget",
                    "blpop",
                    "brpop",
                    "lindex",
                    "llen",
                    "lpop",
                    "lrange",
                    "rpop",
                    "scard",
                    "srandmember",
                    "spop",
                    "sscan",
                    "smove",
                    "zlexcount",
                    "zscore",
                    "zscan",
                    "zcard",
                    "zcount",
                    "xget",
                    "get",
                    "xread",
                    "xlen",
                    "xrange",
                    "xrevrange"
            ));
        }
    }
}
