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

package org.apache.skywalking.apm.agent.core.plugin.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith; // 匹配带有指定注解的元素
import static net.bytebuddy.matcher.ElementMatchers.isInterface; // 匹配接口
import static net.bytebuddy.matcher.ElementMatchers.named; // 匹配名称与给定字符串相等的元素
import static net.bytebuddy.matcher.ElementMatchers.not; // 匹配不满足给定条件的元素

/**
 * Match the class by the given annotations in class.
 *
 * <pre>
 * 基于 类的注解 进行匹配，可设置同时匹配多个（必须全部匹配）。例如："@RequestMapping"。
 * </pre>
 */
public class ClassAnnotationMatch implements IndirectMatch {
    /** 需要全部匹配的注解类 */
    private String[] annotations;

    private ClassAnnotationMatch(String[] annotations) {
        if (annotations == null || annotations.length == 0) {
            throw new IllegalArgumentException("annotations is null");
        }
        this.annotations = annotations;
    }

    /** 匹配所有的 this.annotations，且 不是接口 */
    @Override
    public ElementMatcher.Junction buildJunction() {
        ElementMatcher.Junction junction = null;
        for (String annotation : annotations) {
            if (junction == null) {
                junction = buildEachAnnotation(annotation);
            } else {
                junction = junction.and(buildEachAnnotation(annotation));
            }
        }
        junction = junction.and(not(isInterface()));
        return junction;
    }

    /** true：匹配所有的 this.annotations */
    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        List<String> annotationList = new ArrayList<String>(Arrays.asList(annotations));
        AnnotationList declaredAnnotations = typeDescription.getDeclaredAnnotations();
        for (AnnotationDescription annotation : declaredAnnotations) {
            annotationList.remove(annotation.getAnnotationType().getActualName());
        }
        return annotationList.isEmpty();
    }

    private ElementMatcher.Junction buildEachAnnotation(String annotationName) {
        return isAnnotatedWith(named(annotationName));
    }

    public static ClassAnnotationMatch byClassAnnotationMatch(String... annotations) {
        return new ClassAnnotationMatch(annotations);
    }
}
