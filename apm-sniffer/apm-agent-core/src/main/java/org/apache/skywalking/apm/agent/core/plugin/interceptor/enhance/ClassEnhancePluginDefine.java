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

package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.EnhanceContext;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;
import org.apache.skywalking.apm.agent.core.plugin.bootstrap.BootstrapInstrumentBoost;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.EnhanceException;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.v2.InstanceMethodsInterceptV2Point;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.v2.StaticMethodsInterceptV2Point;
import org.apache.skywalking.apm.util.StringUtil;

import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_VOLATILE;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * This class controls all enhance operations, including enhance constructors, instance methods and static methods. All
 * the enhances base on three types interceptor point: {@link ConstructorInterceptPoint}, {@link
 * InstanceMethodsInterceptPoint} and {@link StaticMethodsInterceptPoint} If plugin is going to enhance constructors,
 * instance methods, or both, {@link ClassEnhancePluginDefine} will add a field of {@link Object} type.
 *
 * <pre>
 * (该类控制所有增强操作，包括增强构造函数、实例方法和静态方法。
 * 所有的增强基于三种类型的拦截点：ConstructorInterceptPoint， InstanceMethodsInterceptPoint 和 StaticMethodsInterceptPoint
 * 如果插件要增强构造函数，实例方法，或两者都增强，ClassEnhancePluginDefine 将添加一个对象类型字段（{@link #CONTEXT_ATTR_NAME}）。)
 *
 * SkyWalking 类增强插件定义抽象类
 * </pre>
 */
public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    private static final ILog LOGGER = LogManager.getLogger(ClassEnhancePluginDefine.class);

    /**
     * Enhance a class to intercept constructors and class instance methods.
     * <pre>
     * (增强类以拦截构造函数和类实例方法。)
     * </pre>
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    @Override
    protected DynamicType.Builder<?> enhanceInstance(TypeDescription typeDescription,
            DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
            EnhanceContext context) throws PluginException {

        // 获得 构造方法的拦截切面 数组。
        ConstructorInterceptPoint[] constructorInterceptPoints = getConstructorsInterceptPoints();
        // 获得 实例方法的拦截切面 数组。
        InstanceMethodsInterceptPoint[] instanceMethodsInterceptPoints = getInstanceMethodsInterceptPoints();
        String enhanceOriginClassName = typeDescription.getTypeName();
        boolean existedConstructorInterceptPoint = false;
        if (constructorInterceptPoints != null && constructorInterceptPoints.length > 0) {
            existedConstructorInterceptPoint = true;
        }
        boolean existedMethodsInterceptPoints = false;
        if (instanceMethodsInterceptPoints != null && instanceMethodsInterceptPoints.length > 0) {
            existedMethodsInterceptPoints = true;
        }
        DelegateNamingResolver delegateNamingResolver = new DelegateNamingResolver(typeDescription.getTypeName(), this);

        // 不进行拦截
        /**
         * nothing need to be enhanced in class instance, maybe need enhance static methods.
         */
        if (!existedConstructorInterceptPoint && !existedMethodsInterceptPoints) {
            return newClassBuilder;
        }

        /**
         * Manipulate class source code.<br/>
         *
         * new class need:<br/>
         * 1.Add field, name {@link #CONTEXT_ATTR_NAME}.
         * 2.Add a field accessor for this field.
         *
         * And make sure the source codes manipulation only occurs once.
         *
         */
        /*
         * (1. 操作类源代码。
         *  新类需要：
         *  1. 添加字段，名称为 {@link CONTEXT_ATTR_NAME}。
         *  2. 为此字段添加字段访问器。
         * )
         *
         * 实现 EnhancedInstance 接口，增加私有变量 CONTEXT_ATTR_NAME。
         *
         * 为目标 Java 类**"自动"实现 org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance 接口。
         * 这样，目标 Java 类就有一个私有变量，拦截器在执行过程中，可以存储状态到该私有变量。QFTODO：
         */
        // 检查当前类是否已经实现了 EnhancedInstance 接口
        if (!typeDescription.isAssignableTo(EnhancedInstance.class)) {
            // 检查当前对象是否已经被扩展
            if (!context.isObjectExtended()) {
                newClassBuilder = newClassBuilder.defineField(
                    // 在 新类 中定义一个名为 CONTEXT_ATTR_NAME 的字段，类型为 Object，修饰符为 private 和 volatile
                    CONTEXT_ATTR_NAME, Object.class, ACC_PRIVATE | ACC_VOLATILE)
                                                // 使 增强类（新类） 实现 EnhancedInstance 接口，用于 set/get 新增的 CONTEXT_ATTR_NAME 字段
                                                 .implement(EnhancedInstance.class)
                                                // 使用 FieldAccessor 拦截器来处理对新增字段的访问
                                                 .intercept(FieldAccessor.ofField(CONTEXT_ATTR_NAME));
                // 标记当前对象已经完成扩展
                context.extendObjectCompleted();
            }
        }

        /*
         * 2. enhance constructors（增强构造方法）
         */
        if (existedConstructorInterceptPoint) {
            for (ConstructorInterceptPoint constructorInterceptPoint : constructorInterceptPoints) {
                if (isBootstrapInstrumentation()) {
                    newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher()) // 匹配
                                                     .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                                                 .to(BootstrapInstrumentBoost
                                                                                                                     .forInternalDelegateClass(constructorInterceptPoint
                                                                                                                         .getConstructorInterceptor()))));
                } else {
                    newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher()) // 匹配
                                                     .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                                                 .to(new ConstructorInter(constructorInterceptPoint
                                                                                                                     .getConstructorInterceptor(), classLoader), delegateNamingResolver.resolve(constructorInterceptPoint))));
                }
            }
        }

        /*
         * 3. enhance instance methods（增强实例方法）
         */
        if (existedMethodsInterceptPoints) {
            for (InstanceMethodsInterceptPoint instanceMethodsInterceptPoint : instanceMethodsInterceptPoints) {
                String interceptor = instanceMethodsInterceptPoint.getMethodsInterceptor();
                if (StringUtil.isEmpty(interceptor)) {
                    throw new EnhanceException("no InstanceMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
                }
                ElementMatcher.Junction<MethodDescription> junction = not(isStatic()).and(instanceMethodsInterceptPoint.getMethodsMatcher());
                if (instanceMethodsInterceptPoint instanceof DeclaredInstanceMethodsInterceptPoint) {
                    junction = junction.and(ElementMatchers.<MethodDescription>isDeclaredBy(typeDescription));
                }
                if (instanceMethodsInterceptPoint.isOverrideArgs()) {
                    if (isBootstrapInstrumentation()) {
                        newClassBuilder = newClassBuilder.method(junction) // 匹配
                                                         .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                    .withBinders(Morph.Binder.install(OverrideCallable.class))
                                                                                    .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                    } else {
                        newClassBuilder = newClassBuilder.method(junction) // 匹配
                                                         .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                    .withBinders(Morph.Binder.install(OverrideCallable.class))
                                                                                    .to(new InstMethodsInterWithOverrideArgs(interceptor, classLoader), delegateNamingResolver.resolve(instanceMethodsInterceptPoint)));
                    }
                } else {
                    if (isBootstrapInstrumentation()) {
                        newClassBuilder = newClassBuilder.method(junction) // 匹配
                                                         .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                    .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                    } else {
                        newClassBuilder = newClassBuilder.method(junction) // 匹配
                                                         .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                    .to(new InstMethodsInter(interceptor, classLoader), delegateNamingResolver.resolve(instanceMethodsInterceptPoint)));
                    }
                }
            }
        }

        return newClassBuilder;
    }

    /**
     * Enhance a class to intercept class static methods.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    @Override
    protected DynamicType.Builder<?> enhanceClass(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
        ClassLoader classLoader) throws PluginException {

        // 获得 静态方法的拦截切面 数组。若为空，不进行增强。
        StaticMethodsInterceptPoint[] staticMethodsInterceptPoints = getStaticMethodsInterceptPoints();
        String enhanceOriginClassName = typeDescription.getTypeName();
        if (staticMethodsInterceptPoints == null || staticMethodsInterceptPoints.length == 0) {
            return newClassBuilder;
        }
        DelegateNamingResolver delegateNamingResolver = new DelegateNamingResolver(typeDescription.getTypeName(), this);

        // 遍历 静态方法的拦截切面 数组，逐个增强静态方法
        for (StaticMethodsInterceptPoint staticMethodsInterceptPoint : staticMethodsInterceptPoints) {
            // 获得 拦截器
            String interceptor = staticMethodsInterceptPoint.getMethodsInterceptor();
            if (StringUtil.isEmpty(interceptor)) {
                throw new EnhanceException("no StaticMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
            }

            // 使用 拦截器 增强静态方法
            if (staticMethodsInterceptPoint.isOverrideArgs()) {
                if (isBootstrapInstrumentation()) {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher())) // 匹配
                                                     .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                .withBinders(Morph.Binder.install(OverrideCallable.class))
                                                                                .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                } else {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher())) // 匹配
                                                     .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                .withBinders(Morph.Binder.install(OverrideCallable.class))
                                                                                .to(new StaticMethodsInterWithOverrideArgs(interceptor), delegateNamingResolver.resolve(staticMethodsInterceptPoint)));
                }
            } else {
                if (isBootstrapInstrumentation()) {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher())) // 匹配
                                                     .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                } else {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher())) // 匹配
                                                     .intercept(MethodDelegation.withDefaultConfiguration() // 拦截
                                                                                .to(new StaticMethodsInter(interceptor), delegateNamingResolver.resolve(staticMethodsInterceptPoint)));
                }
            }

        }

        return newClassBuilder;
    }

    /**
     * @return null, means enhance no v2 instance methods.
     */
    @Override
    public InstanceMethodsInterceptV2Point[] getInstanceMethodsInterceptV2Points() {
        return null;
    }

    /**
     * @return null, means enhance no v2 static methods.
     */
    @Override
    public StaticMethodsInterceptV2Point[] getStaticMethodsInterceptV2Points() {
        return null;
    }

}
