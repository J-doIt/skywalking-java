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

package net.bytebuddy.agent.builder;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isExtensionClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * A custom AgentBuilder.Default for changing NativeMethodStrategy
 * <pre>
 * SWAgentBuilderDefault 是 SkyWalking 特定的 AgentBuilder 配置类，
 * 用于定制 ByteBuddy 的行为以适应 SkyWalking 的监控和追踪需求。
 * </pre>
 */
public class SWAgentBuilderDefault extends AgentBuilder.Default {

    /**
     * The default circularity lock that assures that no agent created by any agent builder within this
     * class loader causes a class loading circularity.
     * <pre>
     * (默认循环锁，用于确保此类加载器中的任何代理生成器创建的代理都不会导致类加载循环。)
     * </pre>
     */
    private static final CircularityLock DEFAULT_LOCK = new CircularityLock.Default();

    /**
     * @param byteBuddy ByteBuddy的核心实例，用于生成和转换字节码。
     * @param nativeMethodStrategy 处理原生方法（native method）的策略，以确保与SkyWalking的兼容。
     */
    public SWAgentBuilderDefault(ByteBuddy byteBuddy, NativeMethodStrategy nativeMethodStrategy) {
        this(byteBuddy,
                Listener.NoOp.INSTANCE, // 监听器策略，此处采用无操作实例
                DEFAULT_LOCK, // 并发控制锁策略
                PoolStrategy.Default.FAST, // 类池策略，快速模式 QFTODO
                TypeStrategy.Default.REBASE, // 类型策略，重新定义基类
                LocationStrategy.ForClassLoader.STRONG, // 位置策略，使用类加载器的强引用
                ClassFileLocator.NoOp.INSTANCE, // 类文件定位器策略，无操作实例
                nativeMethodStrategy, // 原生方法策略
                WarmupStrategy.NoOp.INSTANCE, // 预热策略，无操作实例
                TransformerDecorator.NoOp.INSTANCE, // 转换器装饰器策略，无操作实例
                new InitializationStrategy.SelfInjection.Split(), // 初始化策略，采用自我注入方式 QFTODO
                RedefinitionStrategy.DISABLED, // 重定义策略，禁用
                RedefinitionStrategy.DiscoveryStrategy.SinglePass.INSTANCE, // 重定义发现策略，单次遍历
                RedefinitionStrategy.BatchAllocator.ForTotal.INSTANCE, // 批量分配器策略，总和实例
                RedefinitionStrategy.Listener.NoOp.INSTANCE, // 重定义监听器策略，无操作实例
                RedefinitionStrategy.ResubmissionStrategy.Disabled.INSTANCE, // 重定义重提交策略，禁用
                InjectionStrategy.UsingReflection.INSTANCE,  // 注入策略，使用反射
                LambdaInstrumentationStrategy.DISABLED, // Lambda 仪器策略，禁用
                DescriptionStrategy.Default.HYBRID, // 描述策略，混合模式
                FallbackStrategy.ByThrowableType.ofOptionalTypes(), // 回退策略，基于异常类型
                ClassFileBufferStrategy.Default.RETAINING, // 类文件缓冲策略，保留
                InstallationListener.NoOp.INSTANCE, // 安装监听器策略，无操作实例
                new RawMatcher.Disjunction( // 匹配器，用于过滤类加载器和特定类名前缀
                        new RawMatcher.ForElementMatchers(any(), isBootstrapClassLoader().or(isExtensionClassLoader())),
                        new RawMatcher.ForElementMatchers(nameStartsWith("net.bytebuddy.")
                                .and(not(ElementMatchers.nameStartsWith(NamingStrategy.BYTE_BUDDY_RENAME_PACKAGE + ".")))
                                .or(nameStartsWith("sun.reflect.").or(nameStartsWith("jdk.internal.reflect.")))
                                .<TypeDescription>or(isSynthetic()))),
                Collections.<Transformation>emptyList()); // 默认空的转换列表
    }

    protected SWAgentBuilderDefault(ByteBuddy byteBuddy, Listener listener, CircularityLock circularityLock, PoolStrategy poolStrategy, TypeStrategy typeStrategy, LocationStrategy locationStrategy, ClassFileLocator classFileLocator, NativeMethodStrategy nativeMethodStrategy, WarmupStrategy warmupStrategy, TransformerDecorator transformerDecorator, InitializationStrategy initializationStrategy, RedefinitionStrategy redefinitionStrategy, RedefinitionStrategy.DiscoveryStrategy redefinitionDiscoveryStrategy, RedefinitionStrategy.BatchAllocator redefinitionBatchAllocator, RedefinitionStrategy.Listener redefinitionListener, RedefinitionStrategy.ResubmissionStrategy redefinitionResubmissionStrategy, InjectionStrategy injectionStrategy, LambdaInstrumentationStrategy lambdaInstrumentationStrategy, DescriptionStrategy descriptionStrategy, FallbackStrategy fallbackStrategy, ClassFileBufferStrategy classFileBufferStrategy, InstallationListener installationListener, RawMatcher ignoreMatcher, List<Transformation> transformations) {
        super(byteBuddy, listener, circularityLock, poolStrategy, typeStrategy, locationStrategy, classFileLocator, nativeMethodStrategy, warmupStrategy, transformerDecorator, initializationStrategy, redefinitionStrategy, redefinitionDiscoveryStrategy, redefinitionBatchAllocator, redefinitionListener, redefinitionResubmissionStrategy, injectionStrategy, lambdaInstrumentationStrategy, descriptionStrategy, fallbackStrategy, classFileBufferStrategy, installationListener, ignoreMatcher, transformations);
    }

}
