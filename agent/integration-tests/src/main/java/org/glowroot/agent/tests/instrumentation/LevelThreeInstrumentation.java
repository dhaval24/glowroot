/*
 * Copyright 2011-2019 the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Message;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.TraceEntry;
import org.glowroot.instrumentation.api.weaving.BindParameter;
import org.glowroot.instrumentation.api.weaving.BindTraveler;
import org.glowroot.instrumentation.api.weaving.OnAfter;
import org.glowroot.instrumentation.api.weaving.OnBefore;
import org.glowroot.instrumentation.api.weaving.Pointcut;

public class LevelThreeInstrumentation {

    @Pointcut(className = "org.glowroot.agent.tests.app.LevelThree", methodName = "call",
            methodParameterTypes = {"java.lang.String", "java.lang.String"},
            timerName = "level three")
    public static class LevelThreeAdvice {

        private static final TimerName timerName = Agent.getTimerName(LevelThreeAdvice.class);

        @OnBefore
        public static TraceEntry onBefore(ThreadContext context, @BindParameter final String arg1,
                @BindParameter final String arg2) {
            return context.startTraceEntry(new MessageSupplier() {
                @Override
                public Message get() {
                    Map<String, String> detail = new LinkedHashMap<String, String>();
                    detail.put("arg1", arg1);
                    detail.put("arg2", arg2);
                    return Message.create("Level Three", detail);
                }
            }, timerName);
        }

        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry) {
            traceEntry.end();
        }
    }
}
