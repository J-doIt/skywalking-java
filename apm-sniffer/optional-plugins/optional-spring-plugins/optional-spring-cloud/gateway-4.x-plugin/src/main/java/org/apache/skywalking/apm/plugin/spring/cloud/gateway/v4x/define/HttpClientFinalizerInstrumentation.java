/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * This class instrument <code>reactor.netty.http.client.HttpClientFinalizer</code> class.
 * <p>
 * This is the class that actually sends the request. By enhancing in different methods,
 * we can get information such as uri and send trace context to the downstream service through http header.
 * </p>
 *
 * <pre>
 * (此类检测 HttpClientFinalizer 类。
 * 这是实际发送请求的类。通过不同的方法进行增强，我们可以获取 uri 等信息，并通过 http header 将 trace context 发送给下游服务。)
 *
 * 增强类：reactor.netty.http.client.HttpClientFinalizer（v1.1.0）
 * 增强构造函数：HttpClientFinalizer(HttpClientConfig config)
 *      拦截器：v4x.HttpClientFinalizerConstructorInterceptor
 * 增强方法1：名为 uri 的方法（有3个）
 *              HttpClient.RequestSender uri(Mono≤String> uri)
 *              HttpClient.RequestSender uri(String uri)
 *              HttpClient.RequestSender uri(URI uri)
 *      拦截器：v4x.HttpClientFinalizerUriInterceptor
 * 增强方法2：HttpClientFinalizer send(BiFunction≤? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher≤Void>> sender)
 *      拦截器：（重写了方法参数）v4x.HttpClientFinalizerSendInterceptor
 * 增强方法3：≤V> Flux≤V> responseConnection(BiFunction≤? super HttpClientResponse, ? super Connection, ? extends Publisher≤V>> receiver)
 *      拦截器：（重写了方法参数）v4x.HttpClientFinalizerResponseConnectionInterceptor
 * </pre>
 */
public class HttpClientFinalizerInstrumentation extends AbstractGatewayV4EnhancePluginDefine {

    private static final String INTERCEPT_CLASS_HTTP_CLIENT_FINALIZER = "reactor.netty.http.client.HttpClientFinalizer";
    private static final String CLIENT_FINALIZER_CONSTRUCTOR_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.HttpClientFinalizerConstructorInterceptor";
    private static final String CLIENT_FINALIZER_CONSTRUCTOR_ARGUMENT_TYPE = "reactor.netty.http.client.HttpClientConfig";
    private static final String HTTP_CLIENT_FINALIZER_URI_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.HttpClientFinalizerUriInterceptor";
    private static final String HTTP_CLIENT_FINALIZER_SEND_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.HttpClientFinalizerSendInterceptor";
    private static final String HTTP_CLIENT_FINALIZER_RESPONSE_CONNECTION_INTERCEPTOR = "org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.HttpClientFinalizerResponseConnectionInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return byName(INTERCEPT_CLASS_HTTP_CLIENT_FINALIZER);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[]{
                new ConstructorInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getConstructorMatcher() {
                        // HttpClientFinalizer 的构造器，且第一个参数为 HttpClientConfig
                        return takesArgumentWithType(0, CLIENT_FINALIZER_CONSTRUCTOR_ARGUMENT_TYPE);
                    }

                    @Override
                    public String getConstructorInterceptor() {
                        return CLIENT_FINALIZER_CONSTRUCTOR_INTERCEPTOR;
                    }
                }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                // 第一个拦截点：
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("uri");
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return HTTP_CLIENT_FINALIZER_URI_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                },
                // 第二个拦截点：
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("send").and(takesArgumentWithType(0, "java.util.function.BiFunction"));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return HTTP_CLIENT_FINALIZER_SEND_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                },
                // 第三个拦截点：
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named("responseConnection");
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return HTTP_CLIENT_FINALIZER_RESPONSE_CONNECTION_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return true;
                    }
                }
        };
    }
}
