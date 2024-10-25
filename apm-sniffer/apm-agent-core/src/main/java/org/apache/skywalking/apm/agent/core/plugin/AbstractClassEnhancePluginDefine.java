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

package org.apache.skywalking.apm.agent.core.plugin;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.v2.InstanceMethodsInterceptV2Point;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.v2.StaticMethodsInterceptV2Point;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.List;

/**
 * Basic abstract class of all sky-walking auto-instrumentation plugins.
 * <p>
 * It provides the outline of enhancing the target class. If you want to know more about enhancing, you should go to see
 * {@link ClassEnhancePluginDefine}
 *
 * <pre>
 * 类增强插件定义抽象基类。
 * 不同插件通过实现 AbstractClassEnhancePluginDefine 抽象类，定义不同框架的切面，记录调用链路。
 * </pre>
 */
public abstract class AbstractClassEnhancePluginDefine {
    private static final ILog LOGGER = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    /**
     * New field name.
     */
    public static final String CONTEXT_ATTR_NAME = "_$EnhancedClassField_ws";

    /**
     * Main entrance of enhancing the class.
     *
     * <pre>
     * (增强 类 的正门)
     * 设置 net.bytebuddy.dynamic.DynamicType.Builder 对象。
     * 通过该对象，定义如何拦截需要修改的目标 Java 类(方法的 transformClassName 参数)。
     * </pre>
     *
     * @param typeDescription target class description.
     * @param builder         byte-buddy's builder to manipulate target class's bytecode.
     * @param classLoader     load the given transformClass
     * @return the new builder, or <code>null</code> if not be enhanced.
     * @throws PluginException when set builder failure.
     */
    public DynamicType.Builder<?> define(TypeDescription typeDescription, DynamicType.Builder<?> builder,
        ClassLoader classLoader, EnhanceContext context) throws PluginException {
        String interceptorDefineClassName = this.getClass().getName();
        String transformClassName = typeDescription.getTypeName();
        if (StringUtil.isEmpty(transformClassName)) {
            LOGGER.warn("classname of being intercepted is not defined by {}.", interceptorDefineClassName);
            return null;
        }

        LOGGER.debug("prepare to enhance class {} by {}.", transformClassName, interceptorDefineClassName);
        WitnessFinder finder = WitnessFinder.INSTANCE;

        /* 判断见证类列表是否都存在。若不存在，则插件不生效 */
        /**
         * find witness classes for enhance class
         */
        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                if (!finder.exist(witnessClass, classLoader)) {
                    LOGGER.warn("enhance class {} by plugin {} is not activated. Witness class {} does not exist.", transformClassName, interceptorDefineClassName, witnessClass);
                    return null;
                }
            }
        }
        List<WitnessMethod> witnessMethods = witnessMethods();
        if (!CollectionUtil.isEmpty(witnessMethods)) {
            for (WitnessMethod witnessMethod : witnessMethods) {
                if (!finder.exist(witnessMethod, classLoader)) {
                    LOGGER.warn("enhance class {} by plugin {} is not activated. Witness method {} does not exist.", transformClassName, interceptorDefineClassName, witnessMethod);
                    return null;
                }
            }
        }

        /* 使用拦截器增强目标类 */
        /**
         * find origin class source code for interceptor
         * (查找 Interceptor 的源类源代码)
         */
        DynamicType.Builder<?> newClassBuilder = this.enhance(typeDescription, builder, classLoader, context);

        /* 标记完成增强 */
        context.initializationStageCompleted();
        LOGGER.debug("enhance class {} by {} completely.", transformClassName, interceptorDefineClassName);

        return newClassBuilder;
    }

    /**
     * Begin to define how to enhance class. After invoke this method, only means definition is finished.
     * <pre>
     * (开始定义如何增强类。调用该方法后，仅表示定义完成。)
     * </pre>
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected DynamicType.Builder<?> enhance(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
                                             ClassLoader classLoader, EnhanceContext context) throws PluginException {
        /* 增强类以拦截类静态方法 */
        newClassBuilder = this.enhanceClass(typeDescription, newClassBuilder, classLoader);

        /* 增强类以拦截构造函数和类实例方法 */
        newClassBuilder = this.enhanceInstance(typeDescription, newClassBuilder, classLoader, context);

        return newClassBuilder;
    }

    /**
     * Enhance a class to intercept constructors and class instance methods.
     *
     * <pre>
     * (增强类以拦截构造函数和类实例方法。)
     * </pre>
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected abstract DynamicType.Builder<?> enhanceInstance(TypeDescription typeDescription,
                                                     DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
                                                     EnhanceContext context) throws PluginException;

    /**
     * Enhance a class to intercept class static methods.
     *
     * <pre>
     * (增强类以拦截类静态方法。)
     * </pre>
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected abstract DynamicType.Builder<?> enhanceClass(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
                                                  ClassLoader classLoader) throws PluginException;

    /**
     * Define the {@link ClassMatch} for filtering class.
     * <pre>
     * (定义了 类匹配( ClassMatch ) ，为了过滤类。)
     * </pre>
     *
     * @return {@link ClassMatch}
     */
    protected abstract ClassMatch enhanceClass();

    /**
     * Witness classname list. Why need witness classname? Let's see like this: A library existed two released versions
     * (like 1.0, 2.0), which include the same target classes, but because of version iterator, they may have the same
     * name, but different methods, or different method arguments list. So, if I want to target the particular version
     * (let's say 1.0 for example), version number is obvious not an option, this is the moment you need "Witness
     * classes". You can add any classes only in this particular release version ( something like class
     * com.company.1.x.A, only in 1.0 ), and you can achieve the goal.
     *
     * <pre>
     * ()
     *
     * 见证类列表。当且仅当应用存在见证类列表，插件才生效。
     * 另外，该方法返回空数组。即默认情况，插件生效，无需见证类列表。
     *
     * 什么意思？比如：一个类库存在两个发布的版本( 如 1.0 和 2.0 )，其中包括相同的目标类，但不同的方法或不同的方法参数列表。
     *      所以我们需要根据库的不同版本使用插件的不同版本。然而版本显然不是一个选项，这时需要使用见证类列表，判断出当前引用类库的发布版本。
     *
     * 举个实际的例子，SpringMVC 3 和 SpringMVC 4 ，都有 @RequestMapping 注解定义 URL 。
     *      通过判断存在 org.springframework.web.servlet.view.xslt.AbstractXsltView 类，应用使用 SpringMVC 3 ，使用 apm-springmvc-annotation-3.x-plugin.jar 。
     *      通过判断存在 org.springframework.web.servlet.tags.ArgumentTag 类，应用使用 SpringMVC 4 ，使用 apm-springmvc-annotation-4.x-plugin.jar 。
     * </pre>
     */
    protected String[] witnessClasses() {
        return new String[] {};
    }

    protected List<WitnessMethod> witnessMethods() {
        return null;
    }

    public boolean isBootstrapInstrumentation() {
        return false;
    }

    /**
     * Constructor methods intercept point. See {@link ConstructorInterceptPoint}
     *
     * <pre>
     * (构造函数方法的切点。见 ConstructorInterceptPoint)
     * </pre>
     *
     * @return collections of {@link ConstructorInterceptPoint}
     */
    public abstract ConstructorInterceptPoint[] getConstructorsInterceptPoints();

    /**
     * Instance methods intercept point. See {@link InstanceMethodsInterceptPoint}
     *
     * @return collections of {@link InstanceMethodsInterceptPoint}
     */
    public abstract InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();

    /**
     * Instance methods intercept v2 point. See {@link InstanceMethodsInterceptV2Point}
     *
     * @return collections of {@link InstanceMethodsInterceptV2Point}
     */
    public abstract InstanceMethodsInterceptV2Point[] getInstanceMethodsInterceptV2Points();

    /**
     * Static methods intercept point. See {@link StaticMethodsInterceptPoint}
     *
     * @return collections of {@link StaticMethodsInterceptPoint}
     */
    public abstract StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints();

    /**
     * Instance methods intercept v2 point. See {@link InstanceMethodsInterceptV2Point}
     *
     * @return collections of {@link InstanceMethodsInterceptV2Point}
     */
    public abstract StaticMethodsInterceptV2Point[] getStaticMethodsInterceptV2Points();
}
