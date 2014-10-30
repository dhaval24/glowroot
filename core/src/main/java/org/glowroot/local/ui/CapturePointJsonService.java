/*
 * Copyright 2013-2014 the original author or authors.
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
package org.glowroot.local.ui;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.common.ObjectMappers;
import org.glowroot.config.CapturePoint;
import org.glowroot.config.ConfigService;
import org.glowroot.config.JsonViews.UiView;
import org.glowroot.local.ui.UiAnalyzedMethod.UiAnalyzedMethodOrdering;
import org.glowroot.markers.Singleton;
import org.glowroot.transaction.AdviceCache;
import org.glowroot.transaction.TransactionModule;

import static org.glowroot.common.ObjectMappers.checkRequiredProperty;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;

/**
 * Json service to support capture points.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
@JsonService
class CapturePointJsonService {

    private static final Logger logger = LoggerFactory.getLogger(CapturePointJsonService.class);
    private static final ObjectMapper mapper = ObjectMappers.create();
    private static final Splitter splitter = Splitter.on(' ').omitEmptyStrings();

    private final ConfigService configService;
    private final AdviceCache adviceCache;
    private final TransactionModule transactionModule;
    private final ClasspathCache classpathCache;

    CapturePointJsonService(ConfigService configService, AdviceCache adviceCache,
            ClasspathCache classpathCache, TransactionModule transactionModule) {
        this.configService = configService;
        this.adviceCache = adviceCache;
        this.classpathCache = classpathCache;
        this.transactionModule = transactionModule;
    }

    @GET("/backend/config/capture-points")
    String getCapturePoint() throws IOException, SQLException {
        logger.debug("getCapturePoint()");
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("configs");
        writer.writeValue(jg, configService.getCapturePoints());
        jg.writeBooleanField("jvmOutOfSync",
                adviceCache.isOutOfSync(configService.getCapturePoints()));
        jg.writeBooleanField("jvmRetransformClassesSupported",
                transactionModule.isJvmRetransformClassesSupported());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    // this is marked as @GET so it can be used without update rights (e.g. demo instance)
    @GET("/backend/config/preload-classpath-cache")
    void preloadClasspathCache() throws IOException {
        logger.debug("preloadClasspathCache()");
        // HttpServer is configured with a very small thread pool to keep number of threads down
        // (currently only a single thread), so spawn a background thread to perform the preloading
        // so it doesn't block other http requests
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                classpathCache.updateCache();
            }
        });
        thread.setDaemon(true);
        thread.setName("Glowroot-Temporary-Thread");
        thread.start();
    }

    @GET("/backend/config/matching-class-names")
    String getMatchingClassNames(String content) throws IOException {
        logger.debug("getMatchingClassNames(): content={}", content);
        ClassNamesRequest request =
                ObjectMappers.readRequiredValue(mapper, content, ClassNamesRequest.class);
        List<String> matchingClassNames = classpathCache.getMatchingClassNames(
                request.getPartialClassName(), request.getLimit());
        return mapper.writeValueAsString(matchingClassNames);
    }

    @GET("/backend/config/matching-method-names")
    String getMatchingMethodNames(String content) throws IOException {
        logger.debug("getMatchingMethodNames(): content={}", content);
        MethodNamesRequest request =
                ObjectMappers.readRequiredValue(mapper, content, MethodNamesRequest.class);
        List<String> matchingMethodNames = getMatchingMethodNames(request.getClassName(),
                request.getPartialMethodName(), request.getLimit());
        return mapper.writeValueAsString(matchingMethodNames);
    }

    @GET("/backend/config/method-signatures")
    String getMethodSignatures(String content) throws IOException {
        logger.debug("getMethodSignatures(): content={}", content);
        MethodSignaturesRequest request =
                ObjectMappers.readRequiredValue(mapper, content, MethodSignaturesRequest.class);
        List<UiAnalyzedMethod> analyzedMethods =
                getAnalyzedMethods(request.getClassName(), request.getMethodName());
        ArrayNode matchingMethods = mapper.createArrayNode();
        for (UiAnalyzedMethod analyzedMethod : analyzedMethods) {
            ObjectNode matchingMethod = mapper.createObjectNode();
            matchingMethod.put("name", analyzedMethod.getName());
            ArrayNode parameterTypes = mapper.createArrayNode();
            for (String parameterType : analyzedMethod.getParameterTypes()) {
                parameterTypes.add(parameterType);
            }
            matchingMethod.set("parameterTypes", parameterTypes);
            matchingMethod.put("returnType", analyzedMethod.getReturnType());
            ArrayNode modifiers = mapper.createArrayNode();
            // strip final and synchronized from displayed modifiers since they have no impact on
            // the weaver's method matching
            int reducedModifiers = analyzedMethod.getModifiers() & ~ACC_FINAL & ~ACC_SYNCHRONIZED;
            String modifierNames = Modifier.toString(reducedModifiers);
            for (String modifier : splitter.split(modifierNames)) {
                modifiers.add(modifier.toLowerCase(Locale.ENGLISH));
            }
            matchingMethod.set("modifiers", modifiers);
            matchingMethods.add(matchingMethod);
        }
        return mapper.writeValueAsString(matchingMethods);
    }

    @POST("/backend/config/capture-points/+")
    String addCapturePoint(String content) throws IOException {
        logger.debug("addCapturePoint(): content={}", content);
        CapturePoint capturePoint =
                ObjectMappers.readRequiredValue(mapper, content, CapturePoint.class);
        configService.insertCapturePoint(capturePoint);
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        writer.writeValue(jg, capturePoint);
        jg.close();
        return sb.toString();
    }

    @POST("/backend/config/capture-points/([0-9a-f]+)")
    String updateCapturePoint(String priorVersion, String content) throws IOException {
        logger.debug("updateCapturePoint(): priorVersion={}, content={}", priorVersion, content);
        CapturePoint capturePoint =
                ObjectMappers.readRequiredValue(mapper, content, CapturePoint.class);
        configService.updateCapturePoint(priorVersion, capturePoint);
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        writer.writeValue(jg, capturePoint);
        jg.close();
        return sb.toString();
    }

    @POST("/backend/config/capture-points/-")
    void removeCapturePoint(String content) throws IOException {
        logger.debug("removeCapturePoint(): content={}", content);
        String version = ObjectMappers.readRequiredValue(mapper, content, String.class);
        configService.deleteCapturePoint(version);
    }

    // returns the first <limit> matching method names, ordered alphabetically (case-insensitive)
    private ImmutableList<String> getMatchingMethodNames(String className,
            String partialMethodName, int limit) {
        String partialMethodNameUpper = partialMethodName.toUpperCase(Locale.ENGLISH);
        Set<String> methodNames = Sets.newHashSet();
        for (UiAnalyzedMethod analyzedMethod : classpathCache.getAnalyzedMethods(className)) {
            if (Modifier.isNative(analyzedMethod.getModifiers())) {
                continue;
            }
            String methodName = analyzedMethod.getName();
            if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
                // static initializers are not supported by weaver
                // (see AdviceMatcher.isMethodNameMatch())
                // and constructors do not support @OnBefore advice at this time
                continue;
            }
            if (methodName.toUpperCase(Locale.ENGLISH).contains(partialMethodNameUpper)) {
                methodNames.add(methodName);
            }
        }
        ImmutableList<String> sortedMethodNames =
                Ordering.from(String.CASE_INSENSITIVE_ORDER).immutableSortedCopy(methodNames);
        if (methodNames.size() > limit) {
            return sortedMethodNames.subList(0, limit);
        } else {
            return sortedMethodNames;
        }
    }

    private List<UiAnalyzedMethod> getAnalyzedMethods(String className, String methodName) {
        // use set to remove duplicate methods (e.g. same class loaded by multiple class loaders)
        Set<UiAnalyzedMethod> analyzedMethods = Sets.newHashSet();
        for (UiAnalyzedMethod analyzedMethod : classpathCache.getAnalyzedMethods(className)) {
            if (Modifier.isNative(analyzedMethod.getModifiers())) {
                continue;
            }
            if (analyzedMethod.getName().equals(methodName)) {
                analyzedMethods.add(analyzedMethod);
            }
        }
        // order methods by accessibility, then by name, then by number of args
        return UiAnalyzedMethodOrdering.INSTANCE.sortedCopy(analyzedMethods);
    }

    private static class ClassNamesRequest {

        private final String partialClassName;
        private final int limit;

        @JsonCreator
        ClassNamesRequest(@JsonProperty("partialClassName") @Nullable String partialClassName,
                @JsonProperty("limit") int limit) throws JsonMappingException {
            checkRequiredProperty(partialClassName, "partialClassName");
            this.partialClassName = partialClassName;
            this.limit = limit;
        }

        private String getPartialClassName() {
            return partialClassName;
        }

        private int getLimit() {
            return limit;
        }
    }

    private static class MethodNamesRequest {

        private final String className;
        private final String partialMethodName;
        private final int limit;

        @JsonCreator
        MethodNamesRequest(@JsonProperty("className") @Nullable String className,
                @JsonProperty("partialMethodName") @Nullable String partialMethodName,
                @JsonProperty("limit") int limit) throws JsonMappingException {
            checkRequiredProperty(className, "className");
            checkRequiredProperty(partialMethodName, "partialMethodName");
            this.className = className;
            this.partialMethodName = partialMethodName;
            this.limit = limit;
        }

        private String getClassName() {
            return className;
        }

        private String getPartialMethodName() {
            return partialMethodName;
        }

        private int getLimit() {
            return limit;
        }
    }

    private static class MethodSignaturesRequest {

        private final String className;
        private final String methodName;

        @JsonCreator
        MethodSignaturesRequest(@JsonProperty("className") @Nullable String className,
                @JsonProperty("methodName") @Nullable String methodName)
                throws JsonMappingException {
            checkRequiredProperty(className, "className");
            checkRequiredProperty(methodName, "methodName");
            this.className = className;
            this.methodName = methodName;
        }

        private String getClassName() {
            return className;
        }

        private String getMethodName() {
            return methodName;
        }
    }
}
