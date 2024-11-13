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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.bytebuddy.AbstractJunction;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.IndirectMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.ProtectiveShieldMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * The <code>PluginFinder</code> represents a finder , which assist to find the one from the given {@link
 * AbstractClassEnhancePluginDefine} list.
 *
 * <pre>
 * 插件发现者
 * </pre>
 */
public class PluginFinder {
    /**
     * 完整类名匹配.map。
     * ≤ 类名, 插件定义list ≥
     */
    private final Map<String, LinkedList<AbstractClassEnhancePluginDefine>> nameMatchDefine = new HashMap<String, LinkedList<AbstractClassEnhancePluginDefine>>();
    /**
     * 非NameMatch的匹配.list。
     */
    private final List<AbstractClassEnhancePluginDefine> signatureMatchDefine = new ArrayList<AbstractClassEnhancePluginDefine>();
    /** 增强的是由启动类加载器加载的类的插件定义.list */
    private final List<AbstractClassEnhancePluginDefine> bootstrapClassMatchDefine = new ArrayList<AbstractClassEnhancePluginDefine>();
    private static boolean IS_PLUGIN_INIT_COMPLETED = false;

    public PluginFinder(List<AbstractClassEnhancePluginDefine> plugins) {

        // 循环 AbstractClassEnhancePluginDefine 对象数组，添加到 nameMatchDefine / signatureMatchDefine 属性，
        //      方便 #find(...) 方法查找 AbstractClassEnhancePluginDefine 对象。
        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            ClassMatch match = plugin.enhanceClass();

            if (match == null) {
                continue;
            }

            // 处理 NameMatch 为匹配的 AbstractClassEnhancePluginDefine 对象，添加到 nameMatchDefine 属性
            if (match instanceof NameMatch) {
                NameMatch nameMatch = (NameMatch) match;
                LinkedList<AbstractClassEnhancePluginDefine> pluginDefines = nameMatchDefine.get(nameMatch.getClassName());
                if (pluginDefines == null) {
                    pluginDefines = new LinkedList<AbstractClassEnhancePluginDefine>();
                    nameMatchDefine.put(nameMatch.getClassName(), pluginDefines);
                }
                pluginDefines.add(plugin);
            } else {
                // 处理非 NameMatch 为匹配的 AbstractClassEnhancePluginDefine 对象，添加到 signatureMatchDefine 属性
                signatureMatchDefine.add(plugin);
            }

            // 如果增强的是由启动类加载器加载的类
            if (plugin.isBootstrapInstrumentation()) {
                bootstrapClassMatchDefine.add(plugin);
            }
        }
    }

    /**
     * 获得类增强插件定义( AbstractClassEnhancePluginDefine )对象。
     *
     * @param typeDescription
     * @return
     */
    public List<AbstractClassEnhancePluginDefine> find(TypeDescription typeDescription) {
        List<AbstractClassEnhancePluginDefine> matchedPlugins = new LinkedList<AbstractClassEnhancePluginDefine>();
        // 以 nameMatchDefine 属性来匹配 AbstractClassEnhancePluginDefine 对象
        String typeName = typeDescription.getTypeName();
        if (nameMatchDefine.containsKey(typeName)) {
            matchedPlugins.addAll(nameMatchDefine.get(typeName));
        }

        // 以 signatureMatchDefine 属性来匹配 AbstractClassEnhancePluginDefine 对象
        for (AbstractClassEnhancePluginDefine pluginDefine : signatureMatchDefine) {
            IndirectMatch match = (IndirectMatch) pluginDefine.enhanceClass();
            if (match.isMatch(typeDescription)) {
                matchedPlugins.add(pluginDefine);
            }
        }

        return matchedPlugins;
    }

    /**
     * 获得全部插件的类匹配，多个插件的类匹配条件以 or 分隔
     */
    public ElementMatcher<? super TypeDescription> buildMatch() {
        // ElementMatcher.Junction 表示多个匹配条件的逻辑组合
        ElementMatcher.Junction judge = new AbstractJunction<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                // 包含于 nameMatchDefine 的 target 则匹配。
                return nameMatchDefine.containsKey(target.getActualName());
            }
        };
        // 根据 各IndirectMatch实现 构建的 ElementMatcher.Junction 进行 OR 操作。
        for (AbstractClassEnhancePluginDefine define : signatureMatchDefine) {
            ClassMatch match = define.enhanceClass();
            if (match instanceof IndirectMatch) {
                judge = judge.or(((IndirectMatch) match).buildJunction());
            }
        }
        // Filter out all matchers returns to exclude pure interface types.
        // （排除纯接口类型。）
        judge = not(isInterface()).and(judge);
        return new ProtectiveShieldMatcher(judge);
    }

    public List<AbstractClassEnhancePluginDefine> getBootstrapClassMatchDefine() {
        return bootstrapClassMatchDefine;
    }

    public static void pluginInitCompleted() {
        IS_PLUGIN_INIT_COMPLETED = true;
    }

    public static boolean isPluginInitCompleted() {
        return IS_PLUGIN_INIT_COMPLETED;
    }
}
