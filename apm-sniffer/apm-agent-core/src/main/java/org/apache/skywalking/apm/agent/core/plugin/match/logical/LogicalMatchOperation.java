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

package org.apache.skywalking.apm.agent.core.plugin.match.logical;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.NegatingMatcher;
import org.apache.skywalking.apm.agent.core.plugin.match.IndirectMatch;

/**
 * Util class to help to construct logical operations on {@link org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch}s
 *
 * <pre>
 * (Util 类，以帮助在 ClassMatches 上构造 逻辑操作)
 * </pre>
 */
public class LogicalMatchOperation {

    /** 逻辑且 匹配 */
    public static IndirectMatch and(final IndirectMatch... matches) {
        return new LogicalAndMatch(matches);
    }

    /** 逻辑或 匹配 */
    public static IndirectMatch or(final IndirectMatch... matches) {
        return new LogicalOrMatch(matches);
    }

    /** 逻辑非 匹配 */
    public static IndirectMatch not(final IndirectMatch match) {
        return new IndirectMatch() {
            @Override
            public ElementMatcher.Junction buildJunction() {
                // NegatingMatcher（否定匹配器）：反转另一个匹配器的匹配结果。
                return new NegatingMatcher(match.buildJunction());
            }

            @Override
            public boolean isMatch(final TypeDescription typeDescription) {
                return !match.isMatch(typeDescription);
            }
        };
    }
}
