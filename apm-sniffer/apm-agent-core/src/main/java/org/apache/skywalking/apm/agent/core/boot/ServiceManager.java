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

package org.apache.skywalking.apm.agent.core.boot;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;

/**
 * The <code>ServiceManager</code> bases on {@link ServiceLoader}, load all {@link BootService} implementations.
 *
 * <pre>
 * BootService 管理器。负责管理、初始化 BootService 实例们。
 * </pre>
 */
public enum ServiceManager {
    INSTANCE;

    private static final ILog LOGGER = LogManager.getLogger(ServiceManager.class);
    /**
     * <pre>
     * BootService 实例映射。
     *
     * map 的 entrys，eg：
     *      SamplingService.class - SamplingService实例
     *      SamplingService.class - TraceIgnoreExtendService实例
     *      SamplingService.class - TraceSamplerCpuPolicyExtendService实例
     * </pre>
     */
    private Map<Class, BootService> bootedServices = Collections.emptyMap();

    public void boot() {
        // 加载所有 BootService 实现类的实例数组（基于 SPI 机制，eg：apm-sniffer/apm-agent-core/src/main/resources/META-INF/services/org.apache.skywalking.apm.agent.core.boot.BootService）
        bootedServices = loadAllServices();

        // 启动之前
        prepare();
        // 启动
        startup();
        // 启动之后
        onComplete();
    }

    public void shutdown() {
        // 根据 bootService 的优先级，倒序 执行 shutdown
        bootedServices.values().stream().sorted(Comparator.comparingInt(BootService::priority).reversed()).forEach(service -> {
            try {
                service.shutdown();
            } catch (Throwable e) {
                LOGGER.error(e, "ServiceManager try to shutdown [{}] fail.", service.getClass().getName());
            }
        });
    }

    /**
     * <pre>
     * 加载所有 BootService 实现类，并返回。
     *
     * 如果 是 @DefaultImplementor，则直接 put 到 map，
     * 如果 不是 @OverrideImplementor，则检查是否 重复加载，
     * 如果 是 @OverrideImplementor，且 其 'OverrideImplementor.value'对应的实现 是 DefaultImplementor，则可以重复 put；
     * 如果 是 @OverrideImplementor，但 其 'OverrideImplementor.value'对应的实现 不是 DefaultImplementor，则不能重复。
     *
     * map 的 entrys，eg：
     *      SamplingService.class - SamplingService实例
     *      SamplingService.class - TraceIgnoreExtendService实例
     *      SamplingService.class - TraceSamplerCpuPolicyExtendService实例
     * </pre>
     *
     * @return BootService 的所有实现类
     */
    private Map<Class, BootService> loadAllServices() {
        Map<Class, BootService> bootedServices = new LinkedHashMap<>();
        List<BootService> allServices = new LinkedList<>();
        // 加载 BootService 实现类的实例，并加入到 allServices。
        load(allServices);
        for (final BootService bootService : allServices) {
            Class<? extends BootService> bootServiceClass = bootService.getClass();
            boolean isDefaultImplementor = bootServiceClass.isAnnotationPresent(DefaultImplementor.class);
            // 如果 BootService实现类 的注解是 '@DefaultImplementor'
            if (isDefaultImplementor) {
                // 如果 bootServiceClass 未被 map 包含，则 put。
                if (!bootedServices.containsKey(bootServiceClass)) {
                    bootedServices.put(bootServiceClass, bootService);
                } else {
                    //ignore the default service
                    // 忽略 重复的 默认bootServiceClass
                }
            } else {
                OverrideImplementor overrideImplementor = bootServiceClass.getAnnotation(OverrideImplementor.class);
                // 如果 BootService实现类 没有被 '@OverrideImplementor' 注解
                if (overrideImplementor == null) {
                    // 如果 '非重写bootServiceClass' 未被 map 包含，则 put。
                    if (!bootedServices.containsKey(bootServiceClass)) {
                        bootedServices.put(bootServiceClass, bootService);
                    } else {
                        // 异常：'非重写bootServiceClass' 不能重复
                        throw new ServiceConflictException("Duplicate service define for :" + bootServiceClass);
                    }
                } else {
                    // 如果是 '重写bootServiceClass'，获取 目标类（OverrideImplementor.value）
                    Class<? extends BootService> targetService = overrideImplementor.value();
                    // 如果 目标类 被 map 包含，
                    if (bootedServices.containsKey(targetService)) {
                        boolean presentDefault = bootedServices.get(targetService)
                                                               .getClass()
                                                               .isAnnotationPresent(DefaultImplementor.class);
                        // 如果 目标类 是 默认实现（被@DefaultImplementor注解）。
                        if (presentDefault) {
                            bootedServices.put(targetService, bootService);
                        } else {
                            // '重写bootServiceClass' 的 OverrideImplementor.value 必须是 '默认实现（DefaultImplementor）'
                            // bootServiceClass服务 覆盖冲突，存在多个服务想要覆盖 targetService。
                            throw new ServiceConflictException(
                                "Service " + bootServiceClass + " overrides conflict, " + "exist more than one service want to override :" + targetService);
                        }
                    } else {
                        bootedServices.put(targetService, bootService);
                    }
                }
            }

        }
        return bootedServices;
    }

    private void prepare() {
        // 根据 bootService 的优先级执行 prepare
        bootedServices.values().stream().sorted(Comparator.comparingInt(BootService::priority)).forEach(service -> {
            try {
                service.prepare();
            } catch (Throwable e) {
                LOGGER.error(e, "ServiceManager try to pre-start [{}] fail.", service.getClass().getName());
            }
        });
    }

    private void startup() {
        // 根据 bootService 的优先级执行 boot
        bootedServices.values().stream().sorted(Comparator.comparingInt(BootService::priority)).forEach(service -> {
            try {
                service.boot();
            } catch (Throwable e) {
                LOGGER.error(e, "ServiceManager try to start [{}] fail.", service.getClass().getName());
            }
        });
    }

    private void onComplete() {
        for (BootService service : bootedServices.values()) {
            try {
                service.onComplete();
            } catch (Throwable e) {
                LOGGER.error(e, "Service [{}] AfterBoot process fails.", service.getClass().getName());
            }
        }
    }

    /**
     * Find a {@link BootService} implementation, which is already started.
     *
     * @param serviceClass class name.
     * @param <T>          {@link BootService} implementation class.
     * @return {@link BootService} instance
     */
    public <T extends BootService> T findService(Class<T> serviceClass) {
        return (T) bootedServices.get(serviceClass);
    }

    /**
     * 基于 SPI 机制，加载 BootService 实现类的实例，并加入到 参数allServices。
     */
    void load(List<BootService> allServices) {
        // 为给定服务创建新的服务加载程序，遍历得到 BootService 服务
        for (final BootService bootService : ServiceLoader.load(BootService.class, AgentClassLoader.getDefault())) {
            allServices.add(bootService);
        }
    }
}
