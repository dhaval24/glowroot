/*
 * Copyright 2018-2019 the original author or authors.
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
package org.glowroot.agent.tests.instrumentation;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.OptionalThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.TraceEntry;
import org.glowroot.instrumentation.api.OptionalThreadContext.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.api.weaving.BindParameter;
import org.glowroot.instrumentation.api.weaving.BindThrowable;
import org.glowroot.instrumentation.api.weaving.BindTraveler;
import org.glowroot.instrumentation.api.weaving.OnBefore;
import org.glowroot.instrumentation.api.weaving.OnReturn;
import org.glowroot.instrumentation.api.weaving.OnThrow;
import org.glowroot.instrumentation.api.weaving.Pointcut;

public class AlreadyInTransactionBehaviorInstrumentation {

    @Pointcut(className = "org.glowroot.agent.tests.app.AlreadyInTransactionBehaviorObject",
            methodName = "call", methodParameterTypes = {"java.lang.String"},
            timerName = "already in transaction behavior one")
    public static class LevelOneAdvice {

        private static final TimerName timerName = Agent.getTimerName(LevelOneAdvice.class);

        @OnBefore
        public static TraceEntry onBefore(OptionalThreadContext context,
                @BindParameter String arg) {
            return context.startTransaction("Test new", "abc",
                    MessageSupplier.create("message: {}", arg), timerName,
                    AlreadyInTransactionBehavior.valueOf(arg));
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
