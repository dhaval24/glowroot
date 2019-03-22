/*
 * Copyright 2013-2019 the original author or authors.
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
package org.glowroot.common.config;

import org.glowroot.instrumentation.config.CustomInstrumentationConfig;
import org.glowroot.instrumentation.config.CustomInstrumentationConfig.AlreadyInTransactionBehavior;
import org.glowroot.instrumentation.config.CustomInstrumentationConfig.CaptureKind;
import org.glowroot.instrumentation.config.CustomInstrumentationConfig.MethodModifier;
import org.glowroot.instrumentation.config.ImmutableCustomInstrumentationConfig;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.wire.api.model.Proto.OptionalInt32;

public class CustomInstrumentationConfigProto {

    private CustomInstrumentationConfigProto() {}

    public static AgentConfig.CustomInstrumentationConfig toProto(
            CustomInstrumentationConfig config) {
        @SuppressWarnings("deprecation")
        AgentConfig.CustomInstrumentationConfig.Builder builder =
                AgentConfig.CustomInstrumentationConfig.newBuilder()
                        .setClassName(config.className())
                        .setClassAnnotation(config.classAnnotation())
                        .setSubTypeRestriction(config.subTypeRestriction())
                        .setSuperTypeRestriction(config.superTypeRestriction())
                        // pointcuts with methodDeclaringClassName are no longer supported in
                        // 0.9.16, but included here to help with transitioning of old
                        // instrumentation config
                        .setMethodDeclaringClassName(config.methodDeclaringClassName())
                        .setMethodName(config.methodName())
                        .setMethodAnnotation(config.methodAnnotation())
                        .addAllMethodParameterType(config.methodParameterTypes())
                        .setMethodReturnType(config.methodReturnType());
        for (MethodModifier methodModifier : config.methodModifiers()) {
            builder.addMethodModifier(toProto(methodModifier));
        }
        builder.setNestingGroup(config.nestingGroup())
                .setOrder(config.order())
                .setCaptureKind(toProto(config.captureKind()))
                .setTransactionType(config.transactionType())
                .setTransactionNameTemplate(config.transactionNameTemplate())
                .setTransactionUserTemplate(config.transactionUserTemplate())
                .putAllTransactionAttributeTemplates(
                        config.transactionAttributeTemplates());
        Integer transactionSlowThresholdMillis = config.transactionSlowThresholdMillis();
        if (transactionSlowThresholdMillis != null) {
            builder.setTransactionSlowThresholdMillis(
                    OptionalInt32.newBuilder().setValue(transactionSlowThresholdMillis));
        }
        AlreadyInTransactionBehavior alreadyInTransactionBehavior =
                config.alreadyInTransactionBehaviorCorrected();
        if (alreadyInTransactionBehavior != null) {
            builder.setAlreadyInTransactionBehavior(toProto(alreadyInTransactionBehavior));
        }
        builder.setTransactionOuter(config.transactionOuter())
                .setTraceEntryMessageTemplate(config.traceEntryMessageTemplate());
        Integer traceEntryStackThresholdMillis = config.traceEntryStackThresholdMillis();
        if (traceEntryStackThresholdMillis != null) {
            builder.setTraceEntryStackThresholdMillis(
                    OptionalInt32.newBuilder().setValue(traceEntryStackThresholdMillis));
        }
        return builder.setTraceEntryCaptureSelfNested(config.traceEntryCaptureSelfNested())
                .setTimerName(config.timerName())
                .setEnabledProperty(config.enabledProperty())
                .setTraceEntryEnabledProperty(config.traceEntryEnabledProperty())
                .build();
    }

    public static ImmutableCustomInstrumentationConfig create(
            AgentConfig.CustomInstrumentationConfig config) {
        @SuppressWarnings("deprecation")
        ImmutableCustomInstrumentationConfig.Builder builder =
                ImmutableCustomInstrumentationConfig.builder()
                        .className(config.getClassName())
                        .classAnnotation(config.getClassAnnotation())
                        .subTypeRestriction(config.getSubTypeRestriction())
                        .superTypeRestriction(config.getSuperTypeRestriction())
                        // pointcuts with methodDeclaringClassName are no longer supported in
                        // 0.9.16, but
                        // included here to help with transitioning of old instrumentation config
                        .methodDeclaringClassName(config.getMethodDeclaringClassName())
                        .methodName(config.getMethodName())
                        .methodAnnotation(config.getMethodAnnotation())
                        .addAllMethodParameterTypes(config.getMethodParameterTypeList())
                        .methodReturnType(config.getMethodReturnType());
        for (AgentConfig.CustomInstrumentationConfig.MethodModifier methodModifier : config
                .getMethodModifierList()) {
            builder.addMethodModifiers(fromProto(methodModifier));
        }
        builder.nestingGroup(config.getNestingGroup())
                .order(config.getOrder())
                .captureKind(fromProto(config.getCaptureKind()))
                .transactionType(config.getTransactionType())
                .transactionNameTemplate(config.getTransactionNameTemplate())
                .transactionUserTemplate(config.getTransactionUserTemplate())
                .putAllTransactionAttributeTemplates(config.getTransactionAttributeTemplatesMap());
        if (config.hasTransactionSlowThresholdMillis()) {
            builder.transactionSlowThresholdMillis(
                    config.getTransactionSlowThresholdMillis().getValue());
        }
        if (config
                .getCaptureKind() == AgentConfig.CustomInstrumentationConfig.CaptureKind.TRANSACTION) {
            builder.alreadyInTransactionBehavior(
                    fromProto(config.getAlreadyInTransactionBehavior()));
        }
        builder.transactionOuter(config.getTransactionOuter())
                .traceEntryMessageTemplate(config.getTraceEntryMessageTemplate());
        if (config.hasTraceEntryStackThresholdMillis()) {
            builder.traceEntryStackThresholdMillis(
                    config.getTraceEntryStackThresholdMillis().getValue());
        }
        return builder.traceEntryCaptureSelfNested(config.getTraceEntryCaptureSelfNested())
                .timerName(config.getTimerName())
                .enabledProperty(config.getEnabledProperty())
                .traceEntryEnabledProperty(config.getTraceEntryEnabledProperty())
                .build();
    }

    private static AgentConfig.CustomInstrumentationConfig.MethodModifier toProto(
            MethodModifier methodModifier) {
        return AgentConfig.CustomInstrumentationConfig.MethodModifier
                .valueOf(methodModifier.name());
    }

    private static AgentConfig.CustomInstrumentationConfig.CaptureKind toProto(
            CaptureKind captureKind) {
        return AgentConfig.CustomInstrumentationConfig.CaptureKind.valueOf(captureKind.name());
    }

    private static AgentConfig.CustomInstrumentationConfig.AlreadyInTransactionBehavior toProto(
            AlreadyInTransactionBehavior alreadyInTransactionBehavior) {
        return AgentConfig.CustomInstrumentationConfig.AlreadyInTransactionBehavior
                .valueOf(alreadyInTransactionBehavior.name());
    }

    private static MethodModifier fromProto(
            AgentConfig.CustomInstrumentationConfig.MethodModifier methodModifier) {
        return MethodModifier.valueOf(methodModifier.name());
    }

    private static CaptureKind fromProto(
            AgentConfig.CustomInstrumentationConfig.CaptureKind captureKind) {
        return CaptureKind.valueOf(captureKind.name());
    }

    private static AlreadyInTransactionBehavior fromProto(
            AgentConfig.CustomInstrumentationConfig.AlreadyInTransactionBehavior alreadyInTransactionBehavior) {
        return AlreadyInTransactionBehavior.valueOf(alreadyInTransactionBehavior.name());
    }
}
