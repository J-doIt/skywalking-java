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

package org.apache.skywalking.apm.plugin.spring.concurrent.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.match.IndirectMatch;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType; // 匹配具有指定超类的类
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith; // 匹配名称以指定前缀开头的元素
import static net.bytebuddy.matcher.ElementMatchers.named; // 匹配名称与给定字符串相等的元素
import static net.bytebuddy.matcher.ElementMatchers.not; // 匹配不满足给定条件的元素

/**
 * {@link EitherInterfaceMatch} match the class inherited {@link #getMatchInterface() } and not inherited {@link
 * #getMutexInterface()}
 *
 * <pre>
 * (EitherInterfaceMatch 继承的 getMatchInterface()  而不是继承的 getMutexInterface() )
 *
 * 任一接口匹配。
 * </pre>
 */
public abstract class EitherInterfaceMatch implements IndirectMatch {

    private static final String SPRING_PACKAGE_PREFIX = "org.springframework";
    private static final String OBJECT_CLASS_NAME = "java.lang.Object";

    protected EitherInterfaceMatch() {

    }

    /** 匹配 非org.springframework开头的，且超类是 getMatchInterface()，且超类不是 getMutexInterface() 的类 */
    @Override
    public ElementMatcher.Junction buildJunction() {
        return not(nameStartsWith(SPRING_PACKAGE_PREFIX)).
                                                             and(hasSuperType(named(getMatchInterface())))
                                                         .and(not(hasSuperType(named(getMutexInterface()))));
    }

    @Override
    public boolean isMatch(TypeDescription typeDescription) {
        MatchResult matchResult = new MatchResult();
        for (TypeDescription.Generic generic : typeDescription.getInterfaces()) {
            // 递归匹配接口及其父接口
            matchHierarchyClazz(generic, matchResult);
        }

        if (typeDescription.getSuperClass() != null) {
            // 递归匹配父类及其父类
            matchHierarchyClazz(typeDescription.getSuperClass(), matchResult);
        }

        return matchResult.result();
    }

    /** 获取 匹配的接口 */
    public abstract String getMatchInterface();

    /** 获取 互斥的接口 */
    public abstract String getMutexInterface();

    private void matchHierarchyClazz(TypeDescription.Generic clazz, MatchResult matchResult) {
        // 如果 clazz 的原始类型 是 “互斥接口”
        if (clazz.asRawType().getTypeName().equals(getMutexInterface())) {
            matchResult.findMutexInterface = true;
            return;
        }

        // 如果 clazz 的原始类型 是 “匹配接口”
        if (clazz.asRawType().getTypeName().equals(getMatchInterface())) {
            matchResult.findMatchInterface = true;
        }

        for (TypeDescription.Generic generic : clazz.getInterfaces()) {
            // 递归匹配接口
            matchHierarchyClazz(generic, matchResult);
        }

        TypeDescription.Generic superClazz = clazz.getSuperClass();
        // 如果父类不为空且不是 Object 类
        if (superClazz != null && !clazz.getTypeName().equals(OBJECT_CLASS_NAME)) {
            // 递归匹配父类
            matchHierarchyClazz(superClazz, matchResult);
        }
    }

    private static class MatchResult {
        private boolean findMatchInterface = false;
        private boolean findMutexInterface = false;

        public boolean result() {
            return findMatchInterface && !findMutexInterface;
        }
    }
}
