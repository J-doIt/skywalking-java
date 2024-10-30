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

import java.util.function.Supplier;

/**
 * The main entrance API of the plugin meter system. {@link Counter}, {@link Gauge}, and {@link Histogram} are created
 * through the MeterFactory.
 *
 * <pre>
 * (插件度量系统的主入口API。通过 MeterFactory 创建 {@link Counter}、{@link Gauge} 和 {@link Histogram}。)
 * </pre>
 */
public class MeterFactory {

    /**
     * Create a counter builder by given meter name
     * <pre>
     * (通过给定的度量名称创建一个计数器（Counter）构建器。)
     * </pre>
     * @param name meter name
     */
    public static Counter.Builder counter(String name) {
        return new Counter.Builder(name);
    }

    /**
     * Create a gauge builder by given meter name and supplier
     * <pre>
     * (通过给定的度量名称和供应商创建一个仪表（Gauge）构建器。)
     * </pre>
     * @param name meter name
     * @param supplier returns the latest value of this gauge
     */
    public static Gauge.Builder gauge(String name, Supplier<Double> supplier) {
        return new Gauge.Builder(name, supplier);
    }

    /**
     * Create a counter builder by given meter name
     * <pre>
     * (通过给定的度量名称创建一个直方图（Histogram）构建器)
     * </pre>
     * @param name meter name
     */
    public static Histogram.Builder histogram(String name) {
        return new Histogram.Builder(name);
    }

}
