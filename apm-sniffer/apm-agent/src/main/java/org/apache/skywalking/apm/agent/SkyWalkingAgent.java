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

package org.apache.skywalking.apm.agent;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.SWAgentBuilderDefault;
import net.bytebuddy.agent.builder.SWDescriptionStrategy;
import net.bytebuddy.agent.builder.SWNativeMethodStrategy;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import org.apache.skywalking.apm.agent.bytebuddy.SWAuxiliaryTypeNamingStrategy;
import net.bytebuddy.implementation.SWImplementationContextFactory;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.bytebuddy.SWMethodGraphCompilerDelegate;
import org.apache.skywalking.apm.agent.bytebuddy.SWMethodNameTransformer;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.SnifferConfigInitializer;
import org.apache.skywalking.apm.agent.core.jvm.LoadedLibraryCollector;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.InstrumentDebuggingClass;
import org.apache.skywalking.apm.agent.core.plugin.PluginBootstrap;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.PluginFinder;
import org.apache.skywalking.apm.agent.core.plugin.bootstrap.BootstrapInstrumentBoost;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.DelegateNamingResolver;
import org.apache.skywalking.apm.agent.core.plugin.jdk9module.JDK9ModuleExporter;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static org.apache.skywalking.apm.agent.core.conf.Constants.NAME_TRAIT;

/**
 * The main entrance of sky-walking agent, based on javaagent mechanism.
 * <pre>
 * (SkyWalking Agent 启动入口，基于 JavaAgent 机制。)
 * </pre>
 */
public class SkyWalkingAgent {
    private static ILog LOGGER = LogManager.getLogger(SkyWalkingAgent.class);

    /**
     * Main entrance. Use byte-buddy transform to enhance all classes, which define in plugins.
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        final PluginFinder pluginFinder;
        try {
            /* 初始化 配置 */
            SnifferConfigInitializer.initializeCoreConfig(agentArgs);
        } catch (Exception e) {
            // try to resolve a new logger, and use the new logger to write the error log here
            LogManager.getLogger(SkyWalkingAgent.class)
                    .error(e, "SkyWalking agent initialized failure. Shutting down.");
            return;
        } finally {
            // refresh logger again after initialization finishes
            LOGGER = LogManager.getLogger(SkyWalkingAgent.class);
        }

        if (!Config.Agent.ENABLE) {
            LOGGER.warn("SkyWalking agent is disabled.");
            return;
        }

        try {
            /* 初始化 插件 */
            pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());
        } catch (AgentPackageNotFoundException ape) {
            LOGGER.error(ape, "Locate agent.jar failure. Shutting down.");
            return;
        } catch (Exception e) {
            LOGGER.error(e, "SkyWalking agent initialized failure. Shutting down.");
            return;
        }

        try {
            /* 基于 byte-buddy ，初始化 Instrumentation 的 java.lang.instrument.ClassFileTransformer */
            installClassTransformer(instrumentation, pluginFinder);
        } catch (Exception e) {
            LOGGER.error(e, "Skywalking agent installed class transformer failure.");
        }

        try {
            /* 初始化 Agent 服务管理 */
            ServiceManager.INSTANCE.boot();
        } catch (Exception e) {
            LOGGER.error(e, "Skywalking agent boot failure.");
        }

        /* 初始化 ShutdownHook */
        Runtime.getRuntime()
               .addShutdownHook(new Thread(ServiceManager.INSTANCE::shutdown/* 关闭 服务管理 */, "skywalking service shutdown thread"));
    }

    /**
     * 基于 byte-buddy ，初始化 Instrumentation 的 java.lang.instrument.ClassFileTransformer
     */
    static void installClassTransformer(Instrumentation instrumentation, PluginFinder pluginFinder) throws Exception {
        LOGGER.info("Skywalking agent begin to install transformer ...");

        /* 创建 AgentBuilder 对象，并设置相关属性。*/
        AgentBuilder agentBuilder = newAgentBuilder()
                // 设置忽略哪些类
                .ignore(
                        nameStartsWith("net.bytebuddy.")
                                .or(nameStartsWith("org.slf4j."))
                                .or(nameStartsWith("org.groovy."))
                                .or(nameContains("javassist"))
                                .or(nameContains(".asm."))
                                .or(nameContains(".reflectasm."))
                                .or(nameStartsWith("sun.reflect"))
                                .or(allSkyWalkingAgentExcludeToolkit())
                                .or(ElementMatchers.isSynthetic()));

        JDK9ModuleExporter.EdgeClasses edgeClasses = new JDK9ModuleExporter.EdgeClasses();
        try {
            // 将必要的类注入到 bootstrap 类加载器中
            agentBuilder = BootstrapInstrumentBoost.inject(pluginFinder, instrumentation, agentBuilder, edgeClasses);
        } catch (Exception e) {
            throw new Exception("SkyWalking agent inject bootstrap instrumentation failure. Shutting down.", e);
        }

        try {
            // 从 JDK 9 开始，引入了模块概念。通过支持这一点，代理核心需要打开读取边缘
            agentBuilder = JDK9ModuleExporter.openReadEdge(instrumentation, agentBuilder, edgeClasses);
        } catch (Exception e) {
            throw new Exception("SkyWalking agent open read edge in JDK 9+ failure. Shutting down.", e);
        }

        // 设置需要拦截的类：
            // 使用已配置好的 AgentBuilder 实例，根据 插件查找器(pluginFinder) 构建的 匹配规则 来定位目标类型
        agentBuilder.type(pluginFinder.buildMatch())
                    // 设置 Java 类的修改逻辑：
                        // 对 匹配到的类型 应用 转换操作（这里使用自定义的Transformer类，它继承或实现了相关的转换逻辑）
                    .transform(new Transformer(pluginFinder))
                    // 设置 重定义策略 为 RETRANSFORMATION，允许在类已被加载后重新定义其结构，适用于运行时修改类的行为
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    // 添加一个自定义的类重定义监听器，用于监听类重定义过程中的事件，如成功、失败等
                    .with(new RedefinitionListener())
                    // 添加一个通用的监听器，可能用于记录日志、统计信息或其他自定义行为
                    .with(new Listener())
                    // 创建 net.bytebuddy.agent.builder.ResettableClassFileTransformer 对象，配置到 Instrumentation 对象上。
                        // 完成配置并安装到当前的Java Instrumentation 对象上，使得字节码操作生效。
                    .installOn(instrumentation);

        // 通知插件初始化完成，这个方法可能用于清理、记录状态或其他收尾工作
        PluginFinder.pluginInitCompleted();

        LOGGER.info("Skywalking agent transformer has installed.");
    }

    /**
     * Create a new agent builder through customized {@link ByteBuddy} powered by
     * {@link SWAuxiliaryTypeNamingStrategy} {@link DelegateNamingResolver} {@link SWMethodNameTransformer} and {@link SWImplementationContextFactory}
     *
     * <pre>
     * (通过定制的 ByteBuddy 创建一个新的代理构建器，该 ByteBuddy 由 SWAuxiliaryTypeNamingStrategy（SW 辅助类型命名策略），DelegateNamingResolver（委托命名解析程序），SWMethodNameTransformer（SW方法名称转换器）
     *      和 SWImplementationContextFactory（SW实现上下文工厂） 提供支持)
     * 初始化一个新的 AgentBuilder 实例，配置了SkyWalking所需的各项定制化策略。
     * </pre>
     * @return 配置好的 AgentBuilder 实例，准备用于后续的字节码操作和代理生成
     */
    private static AgentBuilder newAgentBuilder() {
        // 创建 ByteBuddy 实例并配置以下策略：
            // 1. 类型验证：根据配置决定是否开启调试类验证
            // 2. 辅助类型命名策略：使用SkyWalking特有的命名策略
            // 3. 实现上下文工厂：提供SkyWalking特定的实现上下文
            // 4. 方法图编译委托：覆盖默认的方法图编译器，使用SkyWalking提供的委托
        final ByteBuddy byteBuddy = new ByteBuddy()
                // TypeValidation：
                    // 用于验证生成或修改的类型定义是否有效的策略设置。
                    // 当你创建或修改类定义时，TypeValidation可以帮助确保生成的字节码是合法的，符合JVM规范，从而减少运行时由于类型定义错误导致的VerifyError或其他类加载异常。
                    // 你可以通过它来决定是否在字节码生成过程中启用类型验证，以及验证的严格程度。
                    // 例如，你可以选择仅在调试模式下启用类型验证来提高开发时的效率，而在生产环境中关闭以减少性能开销。
                .with(TypeValidation.of(Config.Agent.IS_OPEN_DEBUGGING_CLASS))
                // AuxiliaryType.NamingStrategy：
                    // 定义了如何为由ByteBuddy生成的辅助类型（auxiliary types）命名的策略。
                    // 辅助类型是在字节码操作过程中动态生成的类，例如为了实现拦截逻辑、持有额外的状态信息或是桥接方法等。
                    // 一个合适的命名策略对于避免类名冲突、便于调试和理解生成的类结构非常重要。
                    // 实现该接口允许用户自定义这些辅助类的命名规则，例如基于原始类型名称生成唯一标识，或是添加特定前缀/后缀以区分它们是由ByteBuddy生成的。
                .with(new SWAuxiliaryTypeNamingStrategy(NAME_TRAIT))
                // Implementation.Context.Factory：
                    // 用于创建 Implementation.Context 实例，该 Context 为ByteBuddy在实施方法拦截或类转换时提供了上下文信息。
                    // 这个工厂允许自定义上下文创建逻辑，上下文包含了关于当前操作的类、方法、类型池以及类加载器等重要信息。
                    // 在执行字节码操作时，这些信息对于正确处理类型引用、操作数栈管理、异常处理及辅助类型生成等任务是必不可少的。
                    // 通过自定义Factory，开发者可以针对特定需求优化或扩展ByteBuddy的功能和性能。
                .with(new SWImplementationContextFactory(NAME_TRAIT))
                // MethodGraph.Compiler：
                    // 用于分析和编译方法调用关系图的接口。
                    // 方法图（Method Graph）表示了类内部及其继承层次中方法之间的调用关系，这对于某些高级字节码操作非常关键，比如实现精确的方法拦截、构造复杂的拦截链路或是进行性能分析。
                    // 通过配置不同的 MethodGraph.Compiler，开发者可以控制方法图的构建细节，比如是否包含过时方法、桥接方法等，以及如何处理泛型等复杂情况。
                    // 这对于确保代理逻辑的准确性和效率是非常重要的，特别是在处理复杂的继承结构和方法覆盖时。
                .with(new SWMethodGraphCompilerDelegate(MethodGraph.Compiler.DEFAULT));

        // 使用配置好的 ByteBuddy 实例创建 AgentBuilder，并进一步配置：
            // 1. SkyWalking原生方法策略：处理特定原生方法的策略
            // 2. 描述策略：为生成的代理类添加描述信息
        return new SWAgentBuilderDefault(byteBuddy, new SWNativeMethodStrategy(NAME_TRAIT))
                // DescriptionStrategy
                    // 负责定义如何描述或呈现类型信息，尤其是在生成代理类或进行类型转换时。
                    // 具体来说，它的主要职责是控制如何命名和组织由ByteBuddy动态生成的类型（如代理类、桥接类等）的元数据，包括类名、包名、修饰符等，以及如何描述这些类型中的成员（如方法、字段）。
                .with(new SWDescriptionStrategy(NAME_TRAIT));
    }

    /**
     * <pre>
     * Transformer 类用于增强指定类型的字节码，通过 ByteBuddy 动态地插入监控或修改逻辑。
     * 此方法会在类加载时被调用，以实现类的动态增强。
     * </pre>
     */
    private static class Transformer implements AgentBuilder.Transformer {
        private PluginFinder pluginFinder;

        Transformer(PluginFinder pluginFinder) {
            this.pluginFinder = pluginFinder;
        }

        /**
         * 重写transform方法，用于实现类定义的转换（增强）。
         *
         * @param builder 原始的类定义构建器，用于构建增强后的类定义。
         * @param typeDescription 当前被处理的类的类型描述。
         * @param classLoader 当前类加载器，用于获取或注册资源。
         * @param javaModule Java模块系统中的当前模块，与Java 9及更高版本的模块系统兼容。
         * @param protectionDomain 当前类的保护域，包含类的权限和来源信息。
         * @return 增强后的类定义构建器，如果未进行任何增强则返回原始的builder。
         */
        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                final TypeDescription typeDescription,
                                                final ClassLoader classLoader,
                                                final JavaModule javaModule,
                                                final ProtectionDomain protectionDomain) {
            // 注册类加载器，以便追踪已加载的库
            LoadedLibraryCollector.registerURLClassLoader(classLoader);
            // 根据 类型描述 查找匹配的插件定义（AbstractClassEnhancePluginDefine）
            List<AbstractClassEnhancePluginDefine> pluginDefines = pluginFinder.find(typeDescription);
            // 如果找到了匹配的插件定义，则开始增强过程
            if (pluginDefines.size() > 0) {
                DynamicType.Builder<?> newBuilder = builder;
                EnhanceContext context = new EnhanceContext();
                // 循环所有匹配的插件定义（AbstractClassEnhancePluginDefine），逐个应用增强逻辑
                for (AbstractClassEnhancePluginDefine define : pluginDefines) {
                    /* 设置 net.bytebuddy.dynamic.DynamicType.Builder 对象。通过该对象，定义如何拦截需要修改的 Java 类。*/
                    DynamicType.Builder<?> possibleNewBuilder = define.define(
                        typeDescription, newBuilder, classLoader, context);
                    // 如果 新构建器 不为空（即增强了类），则更新构建器
                    if (possibleNewBuilder != null) {
                        newBuilder = possibleNewBuilder;
                    }
                }
                if (context.isEnhanced()) {
                    LOGGER.debug("Finish the prepare stage for {}.", typeDescription.getName());
                }

                return newBuilder;
            }

            LOGGER.debug("Matched class {}, but ignore by finding mechanism.", typeDescription.getTypeName());
            return builder;
        }
    }

    private static ElementMatcher.Junction<NamedElement> allSkyWalkingAgentExcludeToolkit() {
        return nameStartsWith("org.apache.skywalking.").and(not(nameStartsWith("org.apache.skywalking.apm.toolkit.")));
    }

    /**
     * AgentBuilder.Listener 用于监听类转换过程中的各种事件。
     */
    private static class Listener implements AgentBuilder.Listener {

        /**
         * 当发现一个新的类时调用此方法
         *
         * @param typeName 类的全限定名
         * @param classLoader 加载该类的类加载器
         * @param module 该类所属的模块
         * @param loaded 该类是否已经被加载
         */
        @Override
        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {

        }

        /**
         * 当一个类被成功转换（增强）时调用此方法
         *
         * @param typeDescription 被转换的类的描述
         * @param classLoader 加载该类的类加载器
         * @param module 该类所属的模块
         * @param loaded 该类是否已经被加载
         * @param dynamicType 转换后的动态类型
         */
        @Override
        public void onTransformation(final TypeDescription typeDescription,
                                     final ClassLoader classLoader,
                                     final JavaModule module,
                                     final boolean loaded,
                                     final DynamicType dynamicType) {
            if (LOGGER.isDebugEnable()) {
                // 如果日志级别为 DEBUG，则记录调试信息
                LOGGER.debug("On Transformation class {}.", typeDescription.getName());
            }

            // 记录增强后的类的信息
            InstrumentDebuggingClass.INSTANCE.log(dynamicType);
        }

        /**
         * 当一个类被忽略（未进行转换）时调用此方法
         *
         * @param typeDescription 被忽略的类的描述
         * @param classLoader 加载该类的类加载器
         * @param module 该类所属的模块
         * @param loaded 该类是否已经被加载
         */
        @Override
        public void onIgnored(final TypeDescription typeDescription,
                              final ClassLoader classLoader,
                              final JavaModule module,
                              final boolean loaded) {

        }

        /**
         * 当一个类的转换（增强）过程中发生错误时调用此方法
         */
        @Override
        public void onError(final String typeName,
                            final ClassLoader classLoader,
                            final JavaModule module,
                            final boolean loaded,
                            final Throwable throwable) {
            LOGGER.error("Enhance class " + typeName + " error.", throwable);
        }

        /**
         * 当所有类的处理完成时调用此方法
         *
         * @param typeName 类的全限定名
         * @param classLoader 加载该类的类加载器
         * @param module 该类所属的模块
         * @param loaded 该类是否已经被加载
         */
        @Override
        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
        }
    }

    /**
     * AgentBuilder.RedefinitionStrategy.Listener 用于监听 类重新定义 过程中的各种事件
     */
    private static class RedefinitionListener implements AgentBuilder.RedefinitionStrategy.Listener {

        /**
         * 当一批类重新定义时调用此方法
         *
         * @param index 当前批次的索引
         * @param batch 当前批次的类列表
         * @param types 所有需要重新定义的类的列表
         */
        @Override
        public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {
            /* do nothing */
        }

        /**
         * 当重新定义过程中发生错误时调用此方法
         *
         * @param index 当前批次的索引
         * @param batch 当前批次的类列表
         * @param throwable 发生的异常
         * @param types 所有需要重新定义的类的列表
         * @return
         */
        @Override
        public Iterable<? extends List<Class<?>>> onError(int index,
                                                          List<Class<?>> batch,
                                                          Throwable throwable,
                                                          List<Class<?>> types) {
            LOGGER.error(throwable, "index={}, batch={}, types={}", index, batch, types);
            // 返回一个空的可迭代对象，表示没有其他批次需要处理
            return Collections.emptyList();
        }

        /**
         * 当所有类的重新定义完成时调用此方法
         *
         * @param amount 成功重新定义的类的数量
         * @param types 所有需要重新定义的类的列表
         * @param failures 失败的批次及其对应的异常
         */
        @Override
        public void onComplete(int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {
            /* do nothing */
        }
    }
}
