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
package org.glowroot.instrumentation.mongodb;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.QueryEntry;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.ThreadContext;
import org.glowroot.instrumentation.api.TimerName;
import org.glowroot.instrumentation.api.checker.Nullable;
import org.glowroot.instrumentation.api.config.ConfigListener;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.BindMethodName;
import org.glowroot.instrumentation.api.weaving.BindReceiver;
import org.glowroot.instrumentation.api.weaving.BindReturn;
import org.glowroot.instrumentation.api.weaving.BindThrowable;
import org.glowroot.instrumentation.api.weaving.BindTraveler;
import org.glowroot.instrumentation.api.weaving.OnBefore;
import org.glowroot.instrumentation.api.weaving.OnReturn;
import org.glowroot.instrumentation.api.weaving.OnThrow;
import org.glowroot.instrumentation.api.weaving.Pointcut;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.mongodb.MongoIterableInstrumentation.MongoIterableMixin;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class CollectionInstrumentation {

    private static final String QUERY_TYPE = "MongoDB";

    private static final ConfigService configService = Agent.getConfigService("mongodb");

    // visibility is provided by memoryBarrier in org.glowroot.config.ConfigService
    private static int stackTraceThresholdMillis;

    static {
        configService.registerConfigListener(new ConfigListener() {
            @Override
            public void onChange() {
                Double value = configService.getDoubleProperty("stackTraceThresholdMillis").value();
                stackTraceThresholdMillis = value == null ? Integer.MAX_VALUE : value.intValue();
            }
        });
    }

    @Shim("com.mongodb.client.MongoCollection")
    public interface MongoCollection {

        @Shim("com.mongodb.MongoNamespace getNamespace()")
        @Nullable
        Object getNamespace();
    }

    @Shim("com.mongodb.DBCollection")
    public interface DBCollection {

        @Nullable
        String getFullName();
    }

    // TODO add MongoCollection.watch()
    @Pointcut(className = "com.mongodb.client.MongoCollection",
            methodName = "count*|distinct|findOneAnd*|mapReduce|bulkWrite|insert*|delete*"
                    + "|replace|update*|drop*|create*|list*|rename*",
            methodParameterTypes = {".."}, nestingGroup = "mongodb", timerName = "mongodb query")
    public static class MongoCollectionAdvice {

        private static final TimerName timerName = Agent.getTimerName(MongoCollectionAdvice.class);

        @OnBefore
        public static @Nullable QueryEntry onBefore(ThreadContext context,
                @BindReceiver MongoCollection collection, @BindMethodName String methodName) {
            Object namespace = collection.getNamespace();
            if (namespace == null) {
                return null;
            }
            String queryText = methodName + " " + namespace.toString();
            return context.startQueryEntry(QUERY_TYPE, queryText,
                    QueryMessageSupplier.create("mongodb query: "), timerName);
        }

        @OnReturn
        public static void onReturn(@BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                queryEntry.endWithLocationStackTrace(stackTraceThresholdMillis, MILLISECONDS);
            }
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t,
                @BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                queryEntry.endWithError(t);
            }
        }
    }

    @Pointcut(className = "com.mongodb.client.MongoCollection", methodName = "find|aggregate",
            methodParameterTypes = {".."}, nestingGroup = "mongodb", timerName = "mongodb query")
    public static class MongoFindAdvice {

        private static final TimerName timerName = Agent.getTimerName(MongoCollectionAdvice.class);

        @OnBefore
        public static @Nullable QueryEntry onBefore(ThreadContext context,
                @BindReceiver MongoCollection collection, @BindMethodName String methodName) {
            Object namespace = collection.getNamespace();
            if (namespace == null) {
                return null;
            }
            String queryText = methodName + " " + namespace.toString();
            return context.startQueryEntry(QUERY_TYPE, queryText,
                    QueryMessageSupplier.create("mongodb query: "), timerName);
        }

        @OnReturn
        public static void onReturn(@BindReturn MongoIterableMixin mongoIterable,
                @BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                if (mongoIterable != null) {
                    mongoIterable.glowroot$setQueryEntry(queryEntry);
                }
                queryEntry.endWithLocationStackTrace(stackTraceThresholdMillis, MILLISECONDS);
            }
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t,
                @BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                queryEntry.endWithError(t);
            }
        }
    }

    // mongodb driver legacy API (prior to 3.7.0)
    @Pointcut(className = "com.mongodb.DBCollection",
            methodName = "count|getCount|distinct|find*|aggregate|group|mapReduce|insert|remove"
                    + "|save|update*|drop*|create*|ensure*|rename*",
            methodParameterTypes = {".."}, nestingGroup = "mongodb", timerName = "mongodb query")
    public static class DBCollectionAdvice {

        private static final TimerName timerName = Agent.getTimerName(DBCollectionAdvice.class);

        @OnBefore
        public static @Nullable QueryEntry onBefore(ThreadContext context,
                @BindReceiver DBCollection collection, @BindMethodName String methodName) {
            if (methodName.equals("getCount")) {
                methodName = "count";
            }
            String queryText = methodName + " " + collection.getFullName();
            return context.startQueryEntry(QUERY_TYPE, queryText,
                    QueryMessageSupplier.create("mongodb query: "), timerName);
        }

        @OnReturn
        public static void onReturn(@BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                queryEntry.endWithLocationStackTrace(stackTraceThresholdMillis, MILLISECONDS);
            }
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t,
                @BindTraveler @Nullable QueryEntry queryEntry) {
            if (queryEntry != null) {
                queryEntry.endWithError(t);
            }
        }
    }
}