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

package org.apache.skywalking.apm.agent.core.conf;

import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.core.LogLevel;
import org.apache.skywalking.apm.agent.core.logging.core.LogOutput;
import org.apache.skywalking.apm.agent.core.logging.core.ResolverType;
import org.apache.skywalking.apm.agent.core.logging.core.WriterFactory;
import org.apache.skywalking.apm.util.Length;

/**
 * This is the core config in sniffer agent.
 * <pre>
 * (这是 嗅探器代理 中的 核心配置。)
 * </pre>
 */
public class Config {

    public static class Agent {
        /**
         * Namespace represents a subnet, such as kubernetes namespace, or 172.10.*.*.
         *
         * @since 8.10.0 namespace would be added as {@link #SERVICE_NAME} suffix.
         *
         * Removed namespace isolating headers in cross process propagation. The HEADER name was
         * `HeaderName:Namespace`.
         *
         * <pre>
         * (Namespace 表示‘子网’，例如 kubernetes namespace 或 172.10.*.*。
         *
         * namespace 将添加为 #SERVICE_NAME 后缀。
         * 删除了 跨进程传播 中的 命名空间隔离标头。HEADER 名称 为 'HeaderName：Namespace'。
         * )
         * </pre>
         */
        @Length(20)
        public static String NAMESPACE = "";

        /**
         * Service name is showed on the UI. Suggestion: set a unique name for each service, service instance nodes
         * share the same code
         *
         * @since 8.10.0 ${service name} = [${group name}::]${logic name}|${NAMESPACE}|${CLUSTER}
         *
         * The group name, namespace and cluster are optional. Once they are all blank, service name would be the final
         * name.
         *
         * <pre>
         * (服务名称 显示在 UI 上。
         * 建议：为每个服务设置唯一的名称，服务实例节点 共享 相同的代码。
         *
         * ${service name} = [${group name}::]${logic name}|${NAMESPACE}|${CLUSTER}
         * 组名、命名空间 和 集群 是可选的。一旦它们都为空，服务名称将是最终名称。
         * )
         * </pre>
         */
        @Length(50)
        public static String SERVICE_NAME = "";

        /**
         * Cluster defines the physical cluster in a data center or same network segment. In one cluster, IP address
         * should be unique identify.
         *
         * The cluster name would be
         *
         * 1. Add as {@link #SERVICE_NAME} suffix.
         *
         * 2. Add as exit span's peer, ${CLUSTER} / original peer
         *
         * 3. Cross Process Propagation Header's value addressUsedAtClient[index=8] (Target address of this request used
         * on the client end).
         *
         * <pre>
         * (Cluster 定义 数据中心 或 同一网段 中的 物理集群。
         * 在一个集群中，IP 地址应该是唯一标识的。
         * 集群名称将为：
         *      1. 添加为 SERVICE_NAME 后缀。
         *      2. 添加作为 exit span 的 peer， ${CLUSTER} / 原始 peer
         *      3. 跨进程传播 标头的值 addressUsedAtClient[index=8]（客户端使用的此请求的目标地址）。)
         * </pre>
         *
         * @since 8.10.0
         */
        @Length(20)
        public static String CLUSTER = "";

        /**
         * Authentication active is based on backend setting, see application.yml for more details. For most scenarios,
         * this needs backend extensions, only basic match auth provided in default implementation.
         *
         * <pre>
         * (身份验证 激活 基于后端设置，参见 application.yml 了解更多详情。
         * 在大多数情况下，这需要后端扩展，只有默认实现中提供的基本 match auth。)
         * QFTODO：身份验证？后端扩展？
         * </pre>
         */
        public static String AUTHENTICATION = "";

        /**
         * Negative or zero means off, by default. {@code #SAMPLE_N_PER_3_SECS} means sampling N {@link TraceSegment} in
         * 3 seconds tops.
         * <pre>
         * (默认情况下，负数或零表示关闭。#SAMPLE_N_PER_3_SECS 表示在 3秒 内采样 N 个 TraceSegment 。)
         *
         * TraceSegment 的采样频率。
         * </pre>
         */
        public static int SAMPLE_N_PER_3_SECS = -1;

        /**
         * If the operation name of the first span is included in this set, this segment should be ignored. Multiple
         * values should be separated by `,`.
         * <pre>
         * (如果第一个 span 的操作名称 包含于 此集中，则应忽略此段。多个值应用 ',' 分隔。)
         * </pre>
         */
        public static String IGNORE_SUFFIX = ".jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg";

        /**
         * The max number of TraceSegmentRef in a single span to keep memory cost estimatable.
         * <pre>
         * (TraceSegmentRef 在单个 span 中 保持 内存成本 可估计的 最大数)
         *
         * 每个span的 TraceSegmentRef 的限制数（AbstractTracingSpan.refs 的 size）
         * </pre>
         */
        public static int TRACE_SEGMENT_REF_LIMIT_PER_SPAN = 500;

        /**
         * The max number of spans in a single segment. Through this config item, SkyWalking keep your application
         * memory cost estimated.
         * <pre>
         * (单个 segment 中 span 的最大数目。通过此配置项，SkyWalking保持应用程序内存成本估算。)
         *
         * 每个segment的 span 个数限制（TraceSegment.spans 的 size）
         * </pre>
         */
        public static int SPAN_LIMIT_PER_SEGMENT = 300;

        /**
         * If true, SkyWalking agent will save all instrumented classes files in `/debugging` folder. SkyWalking team
         * may ask for these files in order to resolve compatible problem.
         * <pre>
         * (如果为 true，SkyWalking agent 会将所有 instrumented 的类文件 保存在 '/debugging' 文件夹中。
         * SkyWalking 团队可能会要求提供这些文件以解决兼容问题。)
         * </pre>
         */
        public static boolean IS_OPEN_DEBUGGING_CLASS = false;

        /**
         * The identifier of the instance
         * <pre>
         * (实例id)
         * </pre>
         */
        @Length(50)
        public volatile static String INSTANCE_NAME = "";

        /**
         * service instance properties in json format. e.g. agent.instance_properties_json = {"org":
         * "apache-skywalking"}
         * <pre>
         * (json格式的服务实例属性。eg：agent.instance_properties_json = {"org": "apache-skywalking"})
         * </pre>
         */
        public static String INSTANCE_PROPERTIES_JSON = "";

        /**
         * How depth the agent goes, when log cause exceptions.
         * <pre>
         * (当日志导致的异常时（span.log()），代理执行的深度。)
         * 控制 发送OAP时的 Log 的异常堆栈深度。
         * </pre>
         */
        public static int CAUSE_EXCEPTION_DEPTH = 5;

        /**
         * Force reconnection period of grpc, based on grpc_channel_check_interval. If count of check grpc channel
         * status more than this number. The channel check will call channel.getState(true) to requestConnection.
         * <pre>
         * (grpc 强制重连接周期，基于 grpc_channel_check_interval 。
         * 如果检查 grpc通道状态的计数 大于 此数。通道检查 将 调用 channel.getState(true) to 请求连接。)
         * </pre>
         */
        public static long FORCE_RECONNECTION_PERIOD = 1;

        /**
         * Limit the length of the operationName to prevent the overlength issue in the storage.
         *
         * <p>NOTICE</p>
         * In the current practice, we don't recommend the length over 190.
         *
         * <pre>
         * (限制 operationName 的长度，以防止存储中出现超长问题。
         * 注意：在目前的做法中，我们不建议长度超过 190。)
         * </pre>
         */
        public static int OPERATION_NAME_THRESHOLD = 150;

        /**
         * Keep tracing even the backend is not available.
         * <pre>
         * (即使后端不可用，也要继续跟踪。)
         * </pre>
         */
        public static boolean KEEP_TRACING = false;

        /**
         * Force open TLS for gRPC channel if true.
         * <pre>
         * (如果为 true，则强制为 gRPC 通道打开 TLS。)
         * </pre>
         */
        public static boolean FORCE_TLS = false;

        /**
         * SSL trusted ca file. If it exists, will enable TLS for gRPC channel.
         * <pre>
         * (SSL 受信任的 ca 文件。如果存在，将为 gRPC 通道启用 TLS。)
         * </pre>
         */
        public static String SSL_TRUSTED_CA_PATH = "ca" + Constants.PATH_SEPARATOR + "ca.crt";

        /**
         * Key cert chain file. If ssl_cert_chain and ssl_key exist, will enable mTLS for gRPC channel.
         * <pre>
         * (密钥证书链文件。如果存在 ssl_cert_chain 和 ssl_key，将为 gRPC 通道启用 mTLS。)
         * </pre>
         */
        public static String SSL_CERT_CHAIN_PATH;

        /**
         * Private key file. If ssl_cert_chain and ssl_key exist, will enable mTLS for gRPC channel.
         * <pre>
         * (私钥文件。如果存在 ssl_cert_chain 和 ssl_key，将为 gRPC 通道启用 mTLS。)
         * </pre>
         */
        public static String SSL_KEY_PATH;

        /**
         * Agent version. This is set by the agent kernel through reading MANIFEST.MF file in the skywalking-agent.jar.
         * <pre>
         * (agent 的版本。这是由 代理内核 通过读取 skywalking-agent.jar 的 MANIFEST.MF 文件设置的。)
         * </pre>
         */
        public static String VERSION = "UNKNOWN";

        /**
         * Enable the agent kernel services and instrumentation.
         * <pre>
         * (启用 代理内核服务 和 instrumentation。)
         * </pre>
         */
        public static boolean ENABLE = true;
    }

    public static class OsInfo {
        /**
         * Limit the length of the ipv4 list size.
         * <pre>
         * (限制 IPv4 列表大小的长度。)
         * </pre>
         */
        public static int IPV4_LIST_SIZE = 10;
    }

    public static class Collector {
        /**
         * grpc channel status check interval
         * <pre>
         * (gRPC 通道状态 检查间隔)
         * </pre>
         */
        public static long GRPC_CHANNEL_CHECK_INTERVAL = 30;
        /**
         * The period in which the agent report a heartbeat to the backend.
         * <pre>
         * (agent 向 后端 报告 心跳检测 的 周期。)
         * </pre>
         */
        public static long HEARTBEAT_PERIOD = 30;
        /**
         * The agent sends the instance properties to the backend every `collector.heartbeat_period *
         * collector.properties_report_period_factor` seconds
         * <pre>
         * (每 collector.heartbeat_period * collector.properties_report_period_factor 秒（心跳周期 * 属性报告周期系数（30 * 10 秒）），
         *  agent 将 服务实例属性 发送到 后端)
         * 服务实例属性报告周期系数
         * </pre>
         */
        public static int PROPERTIES_REPORT_PERIOD_FACTOR = 10;
        /**
         * Collector skywalking trace receiver service addresses.
         * <pre>
         * (Collector skywalking 追踪接收器服务地址。)
         *
         * 配置文件所在固定路径为 ${AGENT_PACKAGE_PATH}/config/agent.config 中的 collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:127.0.0.1:11800}
         * </pre>
         */
        public static String BACKEND_SERVICE = "";
        /**
         * How long grpc client will timeout in sending data to upstream.
         * <pre>
         * (grpc 客户端 将数据发送到 上游 时将超时多长时间。)
         * grpc客户端 请求 OAP 的超时时间
         * </pre>
         */
        public static int GRPC_UPSTREAM_TIMEOUT = 30;
        /**
         * Get profile task list interval
         * <pre>
         * (获取 分析任务列表 的间隔)
         * </pre>
         */
        public static int GET_PROFILE_TASK_INTERVAL = 20;
        /**
         * Get agent dynamic config interval
         * <pre>
         * (获取 代理动态配置 的间隔)
         * </pre>
         */
        public static int GET_AGENT_DYNAMIC_CONFIG_INTERVAL = 20;
        /**
         * If true, skywalking agent will enable periodically resolving DNS to update receiver service addresses.
         * <pre>
         * (如果为 true，skywalking agent 将启用定期解析 DNS 以更新接收方服务地址。)
         * </pre>
         */
        public static boolean IS_RESOLVE_DNS_PERIODICALLY = false;
    }

    public static class Profile {
        /**
         * If true, skywalking agent will enable profile when user create a new profile task. Otherwise disable
         * profile.
         * <pre>
         * (如果为 true， skywalking agent 将在用户创建新的 分析任务 时 启用分析。否则禁用 分析。)
         * </pre>
         */
        public static boolean ACTIVE = true;

        /**
         * Parallel monitor endpoint thread count
         * <pre>
         * (并行监视器 endpoint 线程计数)
         *
         * ProfileTaskExecutionContext.profilingSegmentSlots 的 size。
         * </pre>
         */
        public static int MAX_PARALLEL = 5;

        /**
         * Max monitoring sub-tasks count of one single endpoint access
         * <pre>
         * (单个 终端节点 访问 的 最大监控子任务数)
         * </pre>
         */
        public static int MAX_ACCEPT_SUB_PARALLEL = 5;

        /**
         * Max monitor segment time(minutes), if current segment monitor time out of limit, then stop it.
         * <pre>
         * (最大监控段时间（分钟），如果当前段监控时间超出限制，则停止监控。)
         * </pre>
         */
        public static int MAX_DURATION = 10;

        /**
         * Max dump thread stack depth
         * （最大 转储线程 堆栈 深度）
         *
         * TracingThreadSnapshot.stackList 的 size
         */
        public static int DUMP_MAX_STACK_DEPTH = 500;

        /**
         * Snapshot transport to backend buffer size
         * <pre>
         * (TracingTread快照 传输到后端时的缓冲区大小)
         *
         * ProfileTaskChannelService.snapshotQueue 的 size
         * </pre>
         */
        public static int SNAPSHOT_TRANSPORT_BUFFER_SIZE = 500;
    }

    public static class Meter {
        /**
         * If true, skywalking agent will enable sending meters. Otherwise disable meter report.
         * <pre>
         * (如果为 true，则 skywalking agent 将启用 sending meters。否则，禁用 meter 报告。)
         * </pre>
         */
        public static boolean ACTIVE = true;

        /**
         * Report meters interval
         * （报告 meters 的间隔）
         */
        public static Integer REPORT_INTERVAL = 20;

        /**
         * Max size of the meter count, using {@link org.apache.skywalking.apm.agent.core.meter.MeterId} as identity
         *
         * <pre>
         * （meter计数的最大大小，使用 MeterId 作为标识）
         *
         * MeterService.meterMap 的 size。（可以注册到 MeterService 的 指标数）
         * </pre>
         */
        public static Integer MAX_METER_SIZE = 500;
    }

    public static class Jvm {
        /**
         * The buffer size of collected JVM info.
         * <pre>
         * (收集的 JVM 信息的缓冲区大小。)
         *
         * JVMMetricsSender.queue 的 size（收集到的 JVMMetric 会存放到这儿）
         * </pre>
         */
        public static int BUFFER_SIZE = 60 * 10;
        /**
         * The period in seconds of JVM metrics collection.
         * <pre>
         * (JVM 指标收集的周期（以秒为单位）。)
         * </pre>
         */
        public static int METRICS_COLLECT_PERIOD = 1;
    }

    public static class Log {
        /**
         * The max size of message to send to server.Default is 10 MB.
         * （要发送到服务器的消息的最大大小。默认值为 10 MB。）
         */
        public static int MAX_MESSAGE_SIZE = 10 * 1024 * 1024;
    }

    /** 内核队列 总共可保存 CHANNEL_SIZE * BUFFER_SIZE */
    public static class Buffer {
        /** DataCarrier 的 channelSize */
        public static int CHANNEL_SIZE = 5;

        /** DataCarrier 的 bufferSize */
        public static int BUFFER_SIZE = 300;
    }

    public static class Logging {
        /**
         * Log file name.
         */
        public static String FILE_NAME = "skywalking-api.log";

        /**
         * Log files directory. Default is blank string, means, use "{theSkywalkingAgentJarDir}/logs  " to output logs.
         * {theSkywalkingAgentJarDir} is the directory where the skywalking agent jar file is located.
         * <p>
         * Ref to {@link WriterFactory#getLogWriter()}
         *
         * <pre>
         * (日志文件目录。默认为空字符串，表示使用 “{theSkywalkingAgentJarDir}/logs” 输出日志。
         * {theSkywalkingAgentJarDir} 是 skywalking 代理 jar 文件所在的目录。)
         * </pre>
         */
        public static String DIR = "";

        /**
         * The max size of log file. If the size is bigger than this, archive the current file, and write into a new
         * file.
         * <pre>
         * (日志文件的最大大小。如果大小大于此大小，请存档当前文件，然后写入新文件。)
         * </pre>
         */
        public static int MAX_FILE_SIZE = 300 * 1024 * 1024;

        /**
         * The max history log files. When rollover happened, if log files exceed this number, then the oldest file will
         * be delete. Negative or zero means off, by default.
         * <pre>
         * (最大历史记录日志文件数。发生 rollover 时，如果日志文件超过此数量，则将删除最旧的文件。默认情况下，负数或零表示关闭。)
         * </pre>
         */
        public static int MAX_HISTORY_FILES = -1;

        /**
         * The log level. Default is debug.
         */
        public static LogLevel LEVEL = LogLevel.DEBUG;

        /**
         * The log output. Default is FILE.
         * <pre>
         * (日志输出（文件、控制台）。默认值为 FILE。)
         * </pre>
         */
        public static LogOutput OUTPUT = LogOutput.FILE;

        /**
         * The log resolver type. Default is PATTERN which will create PatternLogResolver later.
         * <pre>
         * (日志解析类型。默认为 PATTERN，稍后将创建 PatternLogResolver。)
         * LOGGER.error、info、.. 时，输出到日志的格式。
         * </pre>
         */
        public static ResolverType RESOLVER = ResolverType.PATTERN;

        /**
         * The log patten. Default is "%level %timestamp %thread %class : %msg %throwable". Each conversion specifiers
         * starts with a percent sign '%' and fis followed by conversion word. There are some default conversion
         * specifiers: %thread = ThreadName %level = LogLevel  {@link LogLevel} %timestamp = The now() who format is
         * 'yyyy-MM-dd HH:mm:ss:SSS' %class = SimpleName of TargetClass %msg = Message of user input %throwable =
         * Throwable of user input %agent_name = ServiceName of Agent {@link Agent#SERVICE_NAME}
         *
         * <pre>
         * (log patten。
         * 默认值为 “%level %timestamp %thread %class : %msg %throwable”。
         * 每个 “转换说明符” 都以百分号“%”开头，fis 后跟 “转换词”。
         * 有一些默认的“转换说明符”：
         *      %thread = ThreadName
         *      %level = LogLevel
         *      %timestamp = 格式为 'yyyy-MM-dd HH:mm:ss:SSS' 的 now()
         *      %class = SimpleName of TargetClass
         *      %msg = 用户输入的消息
         *      %throwable = 用户输入的可抛出物
         *      %agent_name = 代理配置的 ServiceName（Agent.SERVICE_NAME）
         * </pre>
         *
         * @see org.apache.skywalking.apm.agent.core.logging.core.PatternLogger#DEFAULT_CONVERTER_MAP
         */
        public static String PATTERN = "%level %timestamp %thread %class : %msg %throwable";
    }

    public static class StatusCheck {
        /**
         * Listed exceptions would not be treated as an error. Because in some codes, the exception is being used as a
         * way of controlling business flow.
         * <pre>
         * (列出的异常不会被视为错误。因为在某些代码中，异常被用作控制业务流的一种方式。)
         * </pre>
         */
        public static String IGNORED_EXCEPTIONS = "";

        /**
         * The max recursive depth when checking the exception traced by the agent. Typically, we don't recommend
         * setting this more than 10, which could cause a performance issue. Negative value and 0 would be ignored,
         * which means all exceptions would make the span tagged in error status.
         * <pre>
         * (检查 agent 跟踪的异常时的最大递归深度。
         * 通常，我们建议不要将其设置为超过 10，这可能会导致性能问题。
         * 负值 和 0 将被忽略，这意味着所有异常都会使 span 标记为错误状态。)
         * </pre>
         */
        public static Integer MAX_RECURSIVE_DEPTH = 1;
    }

    public static class Plugin {
        /**
         * Control the length of the peer field.
         * <pre>
         * (控制 peer字段 的长度。)
         * </pre>
         */
        public static int PEER_MAX_LENGTH = 200;

        /**
         * Exclude activated plugins
         * <pre>
         * (排除 已激活的 插件)
         *
         * Agent 加载 插件时，排查这些插件。
         *
         * PluginDefine.name 小写，比如：plugin.exclude_plugins=tomcat-10.x,dubbo-3.x
         * </pre>
         */
        public static String EXCLUDE_PLUGINS = "";

        /**
         * Mount the folders of the plugins. The folder path is relative to agent.jar.
         * <pre>
         * (挂载插件的文件夹。文件夹路径是相对于 agent.jar 的。)
         * 默认的相对目录：plugins 和 activations
         * </pre>
         */
        public static List<String> MOUNT = Arrays.asList("plugins", "activations");
    }

    /**
     * <pre>
     * 关联上下文 配置。
     * 关联上下文，用于传播用户自定义数据。
     * </pre>
     */
    public static class Correlation {
        /**
         * Max element count in the correlation context.
         * <pre>
         * (关联上下文中的最大元素计数。)
         * </pre>
         */
        public static int ELEMENT_MAX_NUMBER = 3;

        /**
         * Max value length of each element.
         * <pre>
         * 每个自定义数据的最大值长度。
         * </pre>
         */
        public static int VALUE_MAX_LENGTH = 128;

        /**
         * Tag the span by the key/value in the correlation context, when the keys listed here exist.
         * <pre>
         * (当 此处列出的 keys 存在时，按 关联上下文 中的 key/value 标记 span。)
         * </pre>
         */
        public static String AUTO_TAG_KEYS = "";
    }
}
