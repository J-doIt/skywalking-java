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

package org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor;

import org.apache.skywalking.apm.plugin.spring.mvc.commons.ParsePathUtil;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

/**
 * The <code>RequestMappingMethodInterceptor</code> only use the first mapping value. it will interceptor with
 * <code>@RequestMapping</code>
 * <pre>
 * (RequestMappingMethodInterceptor 只使用第一个mapping值。它将使用 @RequestMapping 进行拦截。)
 *
 * 增强类：被注解了 Controller 的类
 * 增强方法：带有 @RequestMapping 注解的方法
 * </pre>
 */
public class RequestMappingMethodInterceptor extends AbstractMethodInterceptor {
    @Override
    public String getRequestURL(Method method) {

        // 使用 ParsePathUtil 工具类 递归解析方法上的 RequestMapping 注解
        return ParsePathUtil.recursiveParseMethodAnnotation(method, m -> {
            String requestURL = null;
            // 获取方法上的 RequestMapping 注解
            RequestMapping methodRequestMapping = AnnotationUtils.getAnnotation(m, RequestMapping.class);
            if (methodRequestMapping != null) {
                // 使用 RequestMapping 注解的第一个mapping值 作为 requestURL
                requestURL = methodRequestMapping.value().length > 0 ? methodRequestMapping.value()[0] : "";
            }
            // 返回解析得到的 requestURL
            return requestURL;
        });
    }
}
