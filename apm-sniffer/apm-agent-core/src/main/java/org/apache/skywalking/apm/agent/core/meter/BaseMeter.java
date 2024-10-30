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

package org.apache.skywalking.apm.agent.core.meter;

import java.util.List;
import java.util.Objects;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;

/**
 * BaseMeter is the basic class of all available meter implementations.
 * It includes all labels and unique id representing this meter.
 * <pre>
 * (BaseMeter 是 所有 可用的 计量器 实现的基本类。它包括表示此 计量器 的 所有标签（Label） 和 唯一id（MeterId）。)
 * </pre>
 */
public abstract class BaseMeter {
    /** 计量器的唯一id*/
    protected final MeterId meterId;

    public BaseMeter(MeterId meterId) {
        this.meterId = meterId;
    }

    /**
     * 获取计量器名称
     */
    public String getName() {
        return meterId.getName();
    }

    /**
     * 根据 tagKey 从 meterId 获取 tagValue
     */
    public String getTag(String tagKey) {
        for (MeterTag tag : meterId.getTags()) {
            if (tag.getKey().equals(tagKey)) {
                return tag.getValue();
            }
        }
        return null;
    }

    /**
     * 将 计量器 转换为 gRPC 消息 Bean
     *
     * @return 如果不需要转换或未更改，则返回 null 以忽略
     */
    public abstract MeterData.Builder transform();

    /**
     * 将所有 tags 转换为 gRPC 消息
     */
    public List<Label> transformTags() {
        return getId().transformTags();
    }

    public MeterId getId() {
        return meterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseMeter baseMeter = (BaseMeter) o;
        return Objects.equals(meterId, baseMeter.meterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId);
    }

}
