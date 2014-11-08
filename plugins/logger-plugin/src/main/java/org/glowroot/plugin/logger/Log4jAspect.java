/*
 * Copyright 2014 the original author or authors.
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
package org.glowroot.plugin.logger;

import java.util.Locale;

import org.glowroot.api.ErrorMessage;
import org.glowroot.api.MessageSupplier;
import org.glowroot.api.MetricName;
import org.glowroot.api.PluginServices;
import org.glowroot.api.TraceEntry;
import org.glowroot.api.weaving.BindMethodName;
import org.glowroot.api.weaving.BindParameter;
import org.glowroot.api.weaving.BindTraveler;
import org.glowroot.api.weaving.IsEnabled;
import org.glowroot.api.weaving.OnAfter;
import org.glowroot.api.weaving.OnBefore;
import org.glowroot.api.weaving.Pointcut;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class Log4jAspect {

    private static final String TRACE_METRIC = "logging";

    private static final PluginServices pluginServices = PluginServices.get("logger");

    @Pointcut(className = "org.apache.log4j.Category", methodName = "warn|error|fatal",
            methodParameterTypes = {"java.lang.Object"}, metricName = TRACE_METRIC)
    public static class LogAdvice {
        private static final MetricName metricName =
                pluginServices.getMetricName(LogAdvice.class);
        @IsEnabled
        @SuppressWarnings("unboxing.of.nullable")
        public static boolean isEnabled() {
            return !LoggerPlugin.inAdvice.get() && pluginServices.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object message,
                @BindMethodName String methodName) {
            LoggerPlugin.inAdvice.set(true);
            if (LoggerPlugin.markTraceAsError(methodName.equals("warn"), false)) {
                pluginServices.setTransactionError(String.valueOf(message));
            }
            return pluginServices.startTraceEntry(
                    MessageSupplier.from("log {}: {}", methodName, String.valueOf(message)),
                    metricName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry,
                @BindParameter Object message) {
            LoggerPlugin.inAdvice.set(false);
            traceEntry.endWithError(ErrorMessage.from(String.valueOf(message)));
        }
    }

    @Pointcut(className = "org.apache.log4j.Category", methodName = "warn|error|fatal",
            methodParameterTypes = {"java.lang.Object", "java.lang.Throwable"},
            metricName = TRACE_METRIC)
    public static class LogWithThrowableAdvice {
        private static final MetricName metricName =
                pluginServices.getMetricName(LogWithThrowableAdvice.class);
        @IsEnabled
        @SuppressWarnings("unboxing.of.nullable")
        public static boolean isEnabled() {
            return !LoggerPlugin.inAdvice.get() && pluginServices.isEnabled();
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object message,
                @BindParameter Throwable t,
                @BindMethodName String methodName) {
            LoggerPlugin.inAdvice.set(true);
            if (LoggerPlugin.markTraceAsError(methodName.equals("warn"), t != null)) {
                pluginServices.setTransactionError(String.valueOf(message));
            }
            return pluginServices.startTraceEntry(
                    MessageSupplier.from("log {}: {}", methodName, String.valueOf(message)),
                    metricName);
        }
        @OnAfter
        public static void onAfter(@BindParameter Object message, @BindParameter Throwable t,
                @BindTraveler TraceEntry traceEntry) {
            LoggerPlugin.inAdvice.set(false);
            if (t == null) {
                traceEntry.endWithError(ErrorMessage.from(String.valueOf(message)));
            } else {
                traceEntry.endWithError(ErrorMessage.from(t.getMessage(), t));
            }
        }
    }

    @Pointcut(className = "org.apache.log4j.Category", methodName = "log",
            methodParameterTypes = {"org.apache.log4j.Priority", "java.lang.Object"},
            metricName = TRACE_METRIC)
    public static class LogWithPriorityAdvice {
        private static final MetricName metricName =
                pluginServices.getMetricName(LogWithPriorityAdvice.class);
        @IsEnabled
        @SuppressWarnings("unboxing.of.nullable")
        public static boolean isEnabled(@BindParameter Object priority) {
            if (LoggerPlugin.inAdvice.get() || !pluginServices.isEnabled()) {
                return false;
            }
            String level = priority.toString();
            return level.equals("FATAL") || level.equals("ERROR") || level.equals("WARN");
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object priority,
                @BindParameter Object message) {
            LoggerPlugin.inAdvice.set(true);
            String level = priority.toString().toLowerCase(Locale.ENGLISH);
            if (LoggerPlugin.markTraceAsError(level.equals("warn"), false)) {
                pluginServices.setTransactionError(String.valueOf(message));
            }
            return pluginServices.startTraceEntry(
                    MessageSupplier.from("log {}: {}", level, String.valueOf(message)),
                    metricName);
        }
        @OnAfter
        public static void onAfter(@BindTraveler TraceEntry traceEntry,
                @BindParameter Object message) {
            LoggerPlugin.inAdvice.set(false);
            traceEntry.endWithError(ErrorMessage.from(String.valueOf(message)));
        }
    }

    @Pointcut(className = "org.apache.log4j.Category", methodName = "log",
            methodParameterTypes = {"org.apache.log4j.Priority", "java.lang.Object",
                    "java.lang.Throwable"}, metricName = TRACE_METRIC)
    public static class LogWithPriorityAndThrowableAdvice {
        private static final MetricName metricName =
                pluginServices.getMetricName(LogWithPriorityAndThrowableAdvice.class);
        @IsEnabled
        @SuppressWarnings("unboxing.of.nullable")
        public static boolean isEnabled(@BindParameter Object priority) {
            if (LoggerPlugin.inAdvice.get() || !pluginServices.isEnabled()) {
                return false;
            }
            String level = priority.toString();
            return level.equals("FATAL") || level.equals("ERROR") || level.equals("WARN");
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object priority,
                @BindParameter Object message,
                @BindParameter Throwable t) {
            LoggerPlugin.inAdvice.set(true);
            String level = priority.toString().toLowerCase(Locale.ENGLISH);
            if (LoggerPlugin.markTraceAsError(level.equals("warn"), t != null)) {
                pluginServices.setTransactionError(String.valueOf(message));
            }
            return pluginServices.startTraceEntry(
                    MessageSupplier.from("log {}: {}", level, String.valueOf(message)),
                    metricName);
        }
        @OnAfter
        public static void onAfter(@SuppressWarnings("unused") @BindParameter Object priority,
                @BindParameter Object message, @BindParameter Throwable t,
                @BindTraveler TraceEntry traceEntry) {
            LoggerPlugin.inAdvice.set(false);
            if (t == null) {
                traceEntry.endWithError(ErrorMessage.from(String.valueOf(message)));
            } else {
                traceEntry.endWithError(ErrorMessage.from(t.getMessage(), t));
            }
        }
    }

    @Pointcut(className = "org.apache.log4j.Category", methodName = "l7dlog",
            methodParameterTypes = {"org.apache.log4j.Priority", "java.lang.String",
                    "java.lang.Throwable"}, metricName = TRACE_METRIC)
    public static class LocalizedLogAdvice {
        private static final MetricName metricName =
                pluginServices.getMetricName(LocalizedLogAdvice.class);
        @IsEnabled
        @SuppressWarnings("unboxing.of.nullable")
        public static boolean isEnabled(@BindParameter Object priority) {
            if (LoggerPlugin.inAdvice.get() || !pluginServices.isEnabled()) {
                return false;
            }
            String level = priority.toString();
            return level.equals("FATAL") || level.equals("ERROR") || level.equals("WARN");
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object priority,
                @BindParameter String key,
                @BindParameter Throwable t) {
            LoggerPlugin.inAdvice.set(true);
            String level = priority.toString().toLowerCase(Locale.ENGLISH);
            if (LoggerPlugin.markTraceAsError(level.equals("warn"), t != null)) {
                pluginServices.setTransactionError(key);
            }
            return pluginServices.startTraceEntry(
                    MessageSupplier.from("log {} (localized): {}", level, key), metricName);
        }
        @OnAfter
        public static void onAfter(@SuppressWarnings("unused") @BindParameter Object priority,
                @BindParameter String key, @BindParameter Throwable t,
                @BindTraveler TraceEntry traceEntry) {
            LoggerPlugin.inAdvice.set(false);
            if (t == null) {
                traceEntry.endWithError(ErrorMessage.from(key));
            } else {
                traceEntry.endWithError(ErrorMessage.from(t.getMessage(), t));
            }
        }
    }

    @Pointcut(className = "org.apache.log4j.Category", methodName = "l7dlog",
            methodParameterTypes = {"org.apache.log4j.Priority", "java.lang.String",
                    "java.lang.Object[]", "java.lang.Throwable"}, metricName = TRACE_METRIC)
    public static class LocalizedLogWithParametersAdvice {
        private static final MetricName metricName =
                pluginServices.getMetricName(LocalizedLogWithParametersAdvice.class);
        @IsEnabled
        @SuppressWarnings("unboxing.of.nullable")
        public static boolean isEnabled(@BindParameter Object priority) {
            if (LoggerPlugin.inAdvice.get() || !pluginServices.isEnabled()) {
                return false;
            }
            String level = priority.toString();
            return level.equals("FATAL") || level.equals("ERROR") || level.equals("WARN");
        }
        @OnBefore
        public static TraceEntry onBefore(@BindParameter Object priority,
                @BindParameter String key,
                @BindParameter Object[] params, @BindParameter Throwable t) {
            LoggerPlugin.inAdvice.set(true);
            String level = priority.toString().toLowerCase(Locale.ENGLISH);
            if (LoggerPlugin.markTraceAsError(level.equals("warn"), t != null)) {
                pluginServices.setTransactionError(key);
            }
            if (params.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(params[i]);
                }
                return pluginServices.startTraceEntry(
                        MessageSupplier.from("log {} (localized): {} [{}]", level, key,
                                sb.toString()), metricName);
            } else {
                return pluginServices.startTraceEntry(
                        MessageSupplier.from("log {} (localized): {}", level, key),
                        metricName);
            }
        }
        @OnAfter
        public static void onAfter(@SuppressWarnings("unused") @BindParameter Object priority,
                @BindParameter String key, @BindParameter Object[] params,
                @BindParameter Throwable t, @BindTraveler TraceEntry traceEntry) {
            LoggerPlugin.inAdvice.set(false);
            StringBuilder sb = new StringBuilder();
            sb.append(key);
            if (params.length > 0) {
                sb.append(" [");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(params[i]);
                }
                sb.append("]");
            }
            if (t == null) {
                traceEntry.endWithError(ErrorMessage.from(sb.toString()));
            } else {
                traceEntry.endWithError(ErrorMessage.from(t.getMessage(), t));
            }
        }
    }
}
