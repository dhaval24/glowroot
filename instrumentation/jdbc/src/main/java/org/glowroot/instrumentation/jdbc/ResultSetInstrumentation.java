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
package org.glowroot.instrumentation.jdbc;

import org.glowroot.instrumentation.api.Agent;
import org.glowroot.instrumentation.api.Logger;
import org.glowroot.instrumentation.api.QueryEntry;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.checker.NonNull;
import org.glowroot.instrumentation.api.config.BooleanProperty;
import org.glowroot.instrumentation.api.config.ConfigService;
import org.glowroot.instrumentation.api.weaving.BindReceiver;
import org.glowroot.instrumentation.api.weaving.BindReturn;
import org.glowroot.instrumentation.api.weaving.BindTraveler;
import org.glowroot.instrumentation.api.weaving.IsEnabled;
import org.glowroot.instrumentation.api.weaving.OnAfter;
import org.glowroot.instrumentation.api.weaving.OnBefore;
import org.glowroot.instrumentation.api.weaving.OnReturn;
import org.glowroot.instrumentation.api.weaving.Pointcut;
import org.glowroot.instrumentation.api.weaving.Shim;
import org.glowroot.instrumentation.jdbc.StatementInstrumentation.HasStatementMirrorMixin;

public class ResultSetInstrumentation {

    private static final Logger logger = Logger.getLogger(ResultSetInstrumentation.class);
    private static final ConfigService configService = Agent.getConfigService("jdbc");

    @Shim("java.sql.ResultSet")
    public interface ResultSet {
        int getRow();
    }

    @Pointcut(className = "java.sql.ResultSet", methodName = "next", methodParameterTypes = {},
            nestingGroup = "jdbc")
    public static class NextAdvice {
        private static final BooleanProperty timerEnabled =
                configService.getBooleanProperty("captureResultSetNavigate");
        @IsEnabled
        public static boolean isEnabled(@BindReceiver HasStatementMirrorMixin resultSet) {
            return timerEnabled.value() && isEnabledCommon(resultSet);
        }
        @OnBefore
        public static Timer onBefore(@BindReceiver HasStatementMirrorMixin resultSet) {
            return onBeforeCommon(resultSet);
        }
        @OnReturn
        public static void onReturn(@BindReturn boolean currentRowValid,
                @BindReceiver HasStatementMirrorMixin resultSet) {
            StatementMirror mirror = resultSet.glowroot$getStatementMirror();
            if (mirror == null) {
                // this shouldn't happen since just checked above in isEnabled(), unless some
                // bizarre concurrent mis-usage of ResultSet
                return;
            }
            QueryEntry lastQueryEntry = mirror.getLastQueryEntry();
            if (lastQueryEntry == null) {
                // tracing must be disabled (e.g. exceeded trace entry limit)
                return;
            }
            if (currentRowValid) {
                // ResultSet.getRow() is sometimes not super duper fast due to ResultSet
                // wrapping and other checks, so this optimizes the common case
                lastQueryEntry.incrementCurrRow();
            } else {
                lastQueryEntry.rowNavigationAttempted();
            }
        }
        @OnAfter
        public static void onAfter(@BindTraveler Timer timer) {
            timer.stop();
        }
    }

    @Pointcut(className = "java.sql.ResultSet",
            methodName = "previous|relative|absolute|first|last", methodParameterTypes = "..",
            nestingGroup = "jdbc")
    public static class NavigateAdvice {
        private static final BooleanProperty timerEnabled =
                configService.getBooleanProperty("captureResultSetNavigate");
        @IsEnabled
        public static boolean isEnabled(@BindReceiver HasStatementMirrorMixin resultSet) {
            return timerEnabled.value() && isEnabledCommon(resultSet);
        }
        @OnBefore
        public static Timer onBefore(@BindReceiver HasStatementMirrorMixin resultSet) {
            return onBeforeCommon(resultSet);
        }
        @OnReturn
        public static void onReturn(@BindReceiver HasStatementMirrorMixin resultSet) {
            try {
                StatementMirror mirror = resultSet.glowroot$getStatementMirror();
                if (mirror == null) {
                    // this shouldn't happen since just checked above in isEnabled(), unless some
                    // bizarre concurrent mis-usage of ResultSet
                    return;
                }
                QueryEntry lastQueryEntry = mirror.getLastQueryEntry();
                if (lastQueryEntry == null) {
                    // tracing must be disabled (e.g. exceeded trace entry limit)
                    return;
                }
                lastQueryEntry.setCurrRow(((ResultSet) resultSet).getRow());
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
        @OnAfter
        public static void onAfter(@BindTraveler Timer timer) {
            timer.stop();
        }
    }

    @Pointcut(className = "java.sql.ResultSet", methodName = "get*",
            methodParameterTypes = {"int", ".."}, nestingGroup = "jdbc")
    public static class ValueAdvice {
        private static final BooleanProperty timerEnabled =
                configService.getBooleanProperty("captureResultSetGet");
        @IsEnabled
        public static boolean isEnabled(@BindReceiver HasStatementMirrorMixin resultSet) {
            return timerEnabled.value() && isEnabledCommon(resultSet);
        }
        @OnBefore
        public static Timer onBefore(@BindReceiver HasStatementMirrorMixin resultSet) {
            return onBeforeCommon(resultSet);
        }
        @OnAfter
        public static void onAfter(@BindTraveler Timer timer) {
            timer.stop();
        }
    }

    @Pointcut(className = "java.sql.ResultSet", methodName = "get*",
            methodParameterTypes = {"java.lang.String", ".."}, nestingGroup = "jdbc")
    public static class ValueAdvice2 {
        private static final BooleanProperty timerEnabled =
                configService.getBooleanProperty("captureResultSetGet");
        @IsEnabled
        public static boolean isEnabled(@BindReceiver HasStatementMirrorMixin resultSet) {
            return timerEnabled.value() && isEnabledCommon(resultSet);
        }
        @OnBefore
        public static Timer onBefore(@BindReceiver HasStatementMirrorMixin resultSet) {
            return onBeforeCommon(resultSet);
        }
        @OnAfter
        public static void onAfter(@BindTraveler Timer timer) {
            timer.stop();
        }
    }

    private static boolean isEnabledCommon(HasStatementMirrorMixin resultSet) {
        StatementMirror mirror = resultSet.glowroot$getStatementMirror();
        return mirror != null && mirror.getLastQueryEntry() != null;
    }

    private static Timer onBeforeCommon(HasStatementMirrorMixin resultSet) {
        @SuppressWarnings("nullness") // just checked above in isEnabledCommon()
        @NonNull
        StatementMirror mirror = resultSet.glowroot$getStatementMirror();
        @SuppressWarnings("nullness") // just checked above in isEnabledCommon()
        @NonNull
        QueryEntry lastQueryEntry = mirror.getLastQueryEntry();
        return lastQueryEntry.extend();
    }
}