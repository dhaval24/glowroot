/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.instrumentation.servlet;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.TraceEntry;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.weaving.BindParameter;
import org.glowroot.instrumentation.api.weaving.BindReceiver;
import org.glowroot.instrumentation.api.weaving.BindReturn;
import org.glowroot.instrumentation.api.weaving.BindThrowable;
import org.glowroot.instrumentation.api.weaving.BindTraveler;
import org.glowroot.instrumentation.api.weaving.Mixin;
import org.glowroot.instrumentation.api.weaving.OnBefore;
import org.glowroot.instrumentation.api.weaving.OnReturn;
import org.glowroot.instrumentation.api.weaving.OnThrow;
import org.glowroot.instrumentation.api.weaving.Pointcut;

public class RequestDispatcherInstrumentation {

    // the field and method names are verbose since they will be mixed in to existing classes
    @Mixin("javax.servlet.RequestDispatcher")
    public abstract static class RequestDispatcherImpl implements RequestDispatcherMixin {

        private transient @Nullable String glowroot$path;

        @Override
        public @Nullable String glowroot$getPath() {
            return glowroot$path;
        }

        @Override
        public void glowroot$setPath(@Nullable String path) {
            glowroot$path = path;
        }
    }

    // the method names are verbose since they will be mixed in to existing classes
    public interface RequestDispatcherMixin {

        @Nullable
        String glowroot$getPath();

        void glowroot$setPath(@Nullable String path);
    }

    @Pointcut(className = "javax.servlet.ServletRequest|javax.servlet.ServletContext",
            methodName = "getRequestDispatcher|getNamedDispatcher",
            methodParameterTypes = {"java.lang.String"}, nestingGroup = "servlet-inner-call")
    public static class GetParameterAdvice {
        @OnReturn
        public static void onReturn(@BindReturn @Nullable RequestDispatcherMixin requestDispatcher,
                @BindParameter @Nullable String path) {
            if (requestDispatcher == null) {
                // seems nothing sensible to do here other than ignore
                return;
            }
            requestDispatcher.glowroot$setPath(path);
        }
    }

    @Pointcut(className = "javax.servlet.RequestDispatcher", methodName = "forward|include",
            methodParameterTypes = {"javax.servlet.ServletRequest",
                    "javax.servlet.ServletResponse"},
            nestingGroup = "servlet-dispatch", timerName = "servlet dispatch")
    public static class DispatchAdvice {
        private static final TimerName timerName = Agent.getTimerName(DispatchAdvice.class);
        @OnBefore
        public static TraceEntry onBefore(ThreadContext context,
                @BindReceiver RequestDispatcherMixin requestDispatcher) {
            return context.startTraceEntry(MessageSupplier.create("servlet dispatch: {}",
                    requestDispatcher.glowroot$getPath()), timerName);
        }
        @OnReturn
        public static void onReturn(@BindTraveler TraceEntry traceEntry) {
            traceEntry.end();
        }
        @OnThrow
        public static void onThrow(@BindThrowable Throwable t,
                @BindTraveler TraceEntry traceEntry) {
            traceEntry.endWithError(t);
        }
    }
}
