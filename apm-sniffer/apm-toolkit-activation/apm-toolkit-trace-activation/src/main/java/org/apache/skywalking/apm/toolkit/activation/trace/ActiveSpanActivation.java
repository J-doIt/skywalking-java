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

package org.apache.skywalking.apm.toolkit.activation.trace;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * {@link TraceAnnotationActivation} enhance the <code>tag</code> method of <code>ActiveSpan</code> by
 * <code>ActiveSpanTagInterceptor</code>.
 *
 * <pre>
 * (TraceAnnotationActivation 通过 ActiveSpanTagInterceptor 增强了 ActiveSpan 的 tag 方法。)
 * </pre>
 */
public class ActiveSpanActivation extends ClassStaticMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.skywalking.apm.toolkit.trace.ActiveSpan";

    private static final String TAG_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanTagInterceptor";
    private static final String TAG_INTERCEPTOR_METHOD_NAME = "tag";

    private static final String ERROR_INTERCEPTOR_METHOD_NAME = "error";
    private static final String ERROR_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanErrorInterceptor";
    private static final String ERROR_MSG_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanErrorMsgInterceptor";
    private static final String ERROR_THROWABLE_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanErrorThrowableInteceptor";

    private static final String DEBUG_INTERCEPTOR_METHOD_NAME = "debug";
    private static final String DEBUG_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanDebugInterceptor";

    private static final String INFO_INTERCEPTOR_METHOD_NAME = "info";
    private static final String INFO_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanInfoInterceptor";

    private static final String SET_OPERATION_NAME_METHOD_NAME = "setOperationName";
    private static final String SET_OPERATION_NAME_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.ActiveSpanSetOperationNameInterceptor";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(TAG_INTERCEPTOR_METHOD_NAME); // 方法名 tag
                }

                @Override
                public String getMethodsInterceptor() {
                    return TAG_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 方法名为 debug，并且其参数列表中的第一个参数类型必须是 java.lang.String
                    return named(DEBUG_INTERCEPTOR_METHOD_NAME).and(takesArgumentWithType(0, "java.lang.String"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return DEBUG_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 方法名为 info，并且其参数列表中的第一个参数类型必须是 java.lang.String
                    return named(INFO_INTERCEPTOR_METHOD_NAME).and(takesArgumentWithType(0, "java.lang.String"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INFO_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 方法名为 error，并且其参数列表中的第一个参数类型必须是 java.lang.Throwable
                    return named(ERROR_INTERCEPTOR_METHOD_NAME).and(takesArgumentWithType(0, "java.lang.Throwable"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return ERROR_THROWABLE_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 方法名为 error，并且其参数列表中的第一个参数类型必须是 java.lang.String
                    return named(ERROR_INTERCEPTOR_METHOD_NAME).and(takesArgumentWithType(0, "java.lang.String"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return ERROR_MSG_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 方法名为 error，并且其参数列表为空
                    return named(ERROR_INTERCEPTOR_METHOD_NAME).and(takesArguments(0));
                }

                @Override
                public String getMethodsInterceptor() {
                    return ERROR_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    // 方法名为 setOperationName，并且其参数列表中的第一个参数类型必须是 java.lang.String
                    return named(SET_OPERATION_NAME_METHOD_NAME).and(takesArgumentWithType(0, "java.lang.String"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return SET_OPERATION_NAME_INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }
}
