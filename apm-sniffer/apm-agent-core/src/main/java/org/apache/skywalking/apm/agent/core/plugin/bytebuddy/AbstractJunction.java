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

package org.apache.skywalking.apm.agent.core.plugin.bytebuddy;

import net.bytebuddy.matcher.ElementMatcher;

/**
 * 实现了 ElementMatcher.Junction 接口。
 * 它提供了基本的 AND 和 OR 操作，用于组合多个匹配条件。
 */
public abstract class AbstractJunction<V> implements ElementMatcher.Junction<V> {

    /**
     * 将当前的 Junction 与另一个 ElementMatcher 进行 AND 操作。
     * @param other 另一个 ElementMatcher
     * @return 返回一个新的 Conjunction，表示两个匹配条件的逻辑 AND
     */
    @Override
    public <U extends V> Junction<U> and(ElementMatcher<? super U> other) {
        return new Conjunction<U>(this, other);
    }

    /**
     * 将当前的 Junction 与另一个 ElementMatcher 进行 OR 操作。
     * @param other 另一个 ElementMatcher
     * @return 返回一个新的 Disjunction，表示两个匹配条件的逻辑 OR
     */
    @Override
    public <U extends V> Junction<U> or(ElementMatcher<? super U> other) {
        return new Disjunction<U>(this, other);
    }
}
