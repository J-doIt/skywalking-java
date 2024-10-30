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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

import java.util.function.Supplier;

/**
 * A gauge is a metric that represents a single numerical value that can arbitrarily go up and down.
 * <pre>
 * (gauge（仪表？） 是一种 计量器，它代表一个可以任意上下的数值。)
 *
 * 表示某个数值在某个时间点的状态。它可以用于监控内存使用量、线程池大小等。
 * </pre>
 */
public class Gauge extends BaseMeter {
    private static final ILog LOGGER = LogManager.getLogger(Gauge.class);
    /** 获取 value 的 函数 */
    protected Supplier<Double> getter;

    public Gauge(MeterId meterId, Supplier<Double> getter) {
        super(meterId);
        this.getter = getter;
    }

    /**
     * Get value
     */
    public double get() {
        final Double data = getter.get();
        return data == null ? 0 : data;
    }

    @Override
    public MeterData.Builder transform() {
        double count;
        try {
            count = get();
        } catch (Exception e) {
            LOGGER.warn(e, "Cannot get the count in meter:{}", meterId.getName());
            return null;
        }

        final MeterData.Builder builder = MeterData.newBuilder();
        builder.setSingleValue(MeterSingleValue.newBuilder()
            .setName(getName())
            .addAllLabels(transformTags())
            .setValue(count).build());

        return builder;
    }

    public static class Builder extends AbstractBuilder<Builder, Gauge> {
        private final Supplier<Double> getter;

        public Builder(String name, Supplier<Double> getter) {
            super(name);
            this.getter = getter;
        }

        @Override
        protected MeterType getType() {
            return MeterType.GAUGE;
        }

        @Override
        protected Gauge create(MeterId meterId) {
            if (getter == null) {
                throw new IllegalArgumentException("getter cannot be null");
            }
            return new Gauge(meterId, getter);
        }
    }
}
