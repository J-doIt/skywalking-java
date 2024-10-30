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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * Agent core level service. It provides the register map for all available {@link BaseMeter} instances and schedules
 * the {@link MeterSender}
 *
 * <pre>
 * (Agent 核心级服务。它为所有可用 BaseMeter 实例 提供 寄存器映射，并安排 MeterSender)
 * </pre>
 */
@DefaultImplementor
public class MeterService implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(MeterService.class);

    /** 所有的指标 */
    private final ConcurrentHashMap<MeterId, BaseMeter> meterMap = new ConcurrentHashMap<>();

    /** 报告指标（this.run） 的 结果 的 凭证 */
    private volatile ScheduledFuture<?> reportMeterFuture;

    /** Meter发送器 */
    private MeterSender sender;

    /**
     * 注册 meter
     * @param meter 指标
     */
    public <T extends BaseMeter> T register(T meter) {
        if (meter == null) {
            return null;
        }
        // 如果 已超出计量系统最大大小 ，将不会报告。
        if (meterMap.size() >= Config.Meter.MAX_METER_SIZE) {
            LOGGER.warn(
                "Already out of the meter system max size [{}], will not report. meter name:{}, meter size:{}",
                    Config.Meter.MAX_METER_SIZE, meter.getName(), meterMap.size());
            return meter;
        }

        // 如果 MeterId 不存在 ，则 put
        final BaseMeter data = meterMap.putIfAbsent(meter.getId(), meter);
        // 上一个 meter 为空，则返回 当前 meter
        return data == null ? meter : (T) data;
    }

    @Override
    public void prepare() {
        // 从 ServiceManager 获取 MeterSender 实例
        sender = ServiceManager.INSTANCE.findService(MeterSender.class);
    }

    @Override
    public void boot() {
        // 确保配置文件中的 Meter 已开启
        if (Config.Meter.ACTIVE) {
            // 每 20s 执行一次 run()
            reportMeterFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("MeterReportService")
            ).scheduleWithFixedDelay(new RunnableWithExceptionProtection(
                this,
                t -> LOGGER.error("Report meters failure.", t)
            ), 0, Config.Meter.REPORT_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        // 取消 reportMeterFuture
        if (reportMeterFuture != null) {
            reportMeterFuture.cancel(true);
        }
        // clear all of the meter report
        // (清除所有 meter报告)
        meterMap.clear();
    }

    @Override
    public void run() {
        if (meterMap.isEmpty()) {
            return;
        }
        // 发送 meters 到 OAP
        sender.send(meterMap, this);
    }

}
