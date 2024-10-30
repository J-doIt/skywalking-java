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

package org.apache.skywalking.apm.plugin.spring.mvc.v5;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.PathMappingCache;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The <code>ControllerConstructorInterceptor</code> intercepts the Controller's constructor, in order to acquire the
 * mapping annotation, if exist.
 * <p>
 * But, you can see we only use the first mapping value, <B>Why?</B>
 * <p>
 * Right now, we intercept the controller by annotation as you known, so we CAN'T know which uri patten is actually
 * matched. Even we know, that costs a lot.
 * <p>
 * If we want to resolve that, we must intercept the Spring MVC core codes, that is not a good choice for now.
 * <p>
 * Comment by @wu-sheng
 *
 * <pre>
 * (
 * ControllerConstructorInterceptor 拦截 Controller 的构造函数，以便获取 mapping 注解（如果存在）。
 * 但是，你可以看到我们只使用了第一个映射值，为什么？
 * 目前，我们通过注解来拦截 Controller，所以我们无法知道实际匹配的是哪个 URI 模式。即使我们知道，这样做也会消耗大量资源。
 * 如果我们想解决这个问题，我们必须拦截 Spring MVC 的核心代码，这在目前不是一个好的选择。
 * )
 *
 * 增强类：被注解了 Controller 或 RestController 的类
 * 增强方法：带有 @RequestMapping 注解的方法
 * </pre>
 */
public class ControllerConstructorInterceptor implements InstanceConstructorInterceptor {

    /**
     * @param objInst 被增强的 被Controller注解了的 类
     * @param allArguments 构造方法的参数对象
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        String basePath = "";
        // 查找类上的 @RequestMapping 注解
        RequestMapping basePathRequestMapping = AnnotationUtils.findAnnotation(objInst.getClass(), RequestMapping.class);
        // 如果 @RequestMapping 注解的 value 属性不为空，则使用第一个值作为 basePath
        if (basePathRequestMapping != null) {
            if (basePathRequestMapping.value().length > 0) {
                basePath = basePathRequestMapping.value()[0];
            } else if (basePathRequestMapping.path().length > 0) {
                // 如果 @RequestMapping 注解的 path 属性不为空，则使用第一个值作为 basePath
                basePath = basePathRequestMapping.path()[0];
            }
        }
        EnhanceRequireObjectCache enhanceRequireObjectCache = new EnhanceRequireObjectCache();
        enhanceRequireObjectCache.setPathMappingCache(new PathMappingCache(basePath));
        // 将 EnhanceRequireObjectCache 对象设置到 增强实例 的 动态字段 中
        objInst.setSkyWalkingDynamicField(enhanceRequireObjectCache);
    }
}
