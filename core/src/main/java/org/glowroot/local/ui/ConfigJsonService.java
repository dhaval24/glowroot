/*
 * Copyright 2011-2014 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.CharStreams;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.common.ObjectMappers;
import org.glowroot.config.AdvancedConfig;
import org.glowroot.config.ConfigService;
import org.glowroot.config.ConfigService.OptimisticLockException;
import org.glowroot.config.JsonViews.UiView;
import org.glowroot.config.PluginConfig;
import org.glowroot.config.PluginDescriptorCache;
import org.glowroot.config.ProfilingConfig;
import org.glowroot.config.StorageConfig;
import org.glowroot.config.TraceConfig;
import org.glowroot.config.UserInterfaceConfig;
import org.glowroot.config.UserInterfaceConfig.CurrentPasswordIncorrectException;
import org.glowroot.config.UserRecordingConfig;
import org.glowroot.local.store.CappedDatabase;
import org.glowroot.local.ui.HttpServer.PortChangeFailedException;
import org.glowroot.markers.Singleton;
import org.glowroot.transaction.TransactionModule;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_FAILED;

/**
 * Json service to read and update config data.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
@JsonService
class ConfigJsonService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigJsonService.class);
    private static final ObjectMapper mapper = ObjectMappers.create();

    private final ConfigService configService;
    private final CappedDatabase cappedDatabase;
    private final PluginDescriptorCache pluginDescriptorCache;
    private final File dataDir;
    private final HttpSessionManager httpSessionManager;
    private final TransactionModule transactionModule;

    @MonotonicNonNull
    private volatile HttpServer httpServer;

    ConfigJsonService(ConfigService configService, CappedDatabase cappedDatabase,
            PluginDescriptorCache pluginDescriptorCache, File dataDir,
            HttpSessionManager httpSessionManager, TransactionModule transactionModule) {
        this.configService = configService;
        this.cappedDatabase = cappedDatabase;
        this.pluginDescriptorCache = pluginDescriptorCache;
        this.dataDir = dataDir;
        this.httpSessionManager = httpSessionManager;
        this.transactionModule = transactionModule;
    }

    void setHttpServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    @GET("/backend/config/trace")
    String getTraceConfig() throws IOException, SQLException {
        logger.debug("getTraceConfig()");
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getTraceConfig());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/config/profiling")
    String getProfilingConfig() throws IOException, SQLException {
        logger.debug("getProfilingConfig()");
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getProfilingConfig());
        jg.writeNumberField("defaultTraceStoreThresholdMillis",
                configService.getTraceConfig().getStoreThresholdMillis());
        jg.writeBooleanField("metricWrapperMethodsActive",
                transactionModule.isMetricWrapperMethods());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/config/user-recording")
    String getUserRecordingConfig() throws IOException, SQLException {
        logger.debug("getUserRecordingConfig()");
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getUserRecordingConfig());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/config/storage")
    String getStorage() throws IOException, SQLException {
        logger.debug("getStorage()");
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getStorageConfig());
        jg.writeStringField("dataDir", dataDir.getCanonicalPath());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/config/user-interface")
    String getUserInterface() throws IOException, SQLException {
        logger.debug("getUserInterface()");
        // this code cannot be reached when httpServer is null
        checkNotNull(httpServer);
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        writeUserInterface(jg, writer);
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/config/advanced")
    String getAdvanced() throws IOException, SQLException {
        logger.debug("getAdvanced()");
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getAdvancedConfig());
        jg.writeBooleanField("metricWrapperMethodsActive",
                transactionModule.isMetricWrapperMethods());
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @GET("/backend/config/plugin/(.+)")
    String getPluginConfig(String pluginId) throws IOException, SQLException {
        logger.debug("getPluginConfig(): pluginId={}", pluginId);
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        jg.writeFieldName("descriptor");
        writer.writeValue(jg, pluginDescriptorCache.getPluginDescriptor(pluginId));
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getPluginConfig(pluginId));
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @POST("/backend/config/trace")
    String updateTraceConfig(String content) throws IOException, SQLException {
        logger.debug("updateTraceConfig(): content={}", content);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        String priorVersion = getAndRemoveVersionNode(configNode);
        TraceConfig config = configService.getTraceConfig();
        TraceConfig.Overlay overlay = TraceConfig.overlay(config);
        mapper.readerForUpdating(overlay).readValue(configNode);
        try {
            configService.updateTraceConfig(overlay.build(), priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        return getTraceConfig();
    }

    @POST("/backend/config/profiling")
    String updateProfilingConfig(String content) throws IOException, SQLException {
        logger.debug("updateProfilingConfig(): content={}", content);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        String priorVersion = getAndRemoveVersionNode(configNode);
        ProfilingConfig config = configService.getProfilingConfig();
        ProfilingConfig.Overlay overlay = ProfilingConfig.overlay(config);
        mapper.readerForUpdating(overlay).readValue(configNode);
        try {
            configService.updateProfilingConfig(overlay.build(), priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        return getProfilingConfig();
    }

    @POST("/backend/config/user-recording")
    String updateUserRecordingConfig(String content) throws IOException, SQLException {
        logger.debug("updateUserRecordingConfig(): content={}", content);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        String priorVersion = getAndRemoveVersionNode(configNode);
        UserRecordingConfig config = configService.getUserRecordingConfig();
        UserRecordingConfig.Overlay overlay = UserRecordingConfig.overlay(config);
        mapper.readerForUpdating(overlay).readValue(configNode);
        try {
            configService.updateUserRecordingConfig(overlay.build(), priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        return getUserRecordingConfig();
    }

    @POST("/backend/config/storage")
    String updateStorageConfig(String content) throws IOException, SQLException {
        logger.debug("updateStorageConfig(): content={}", content);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        String priorVersion = getAndRemoveVersionNode(configNode);
        StorageConfig config = configService.getStorageConfig();
        StorageConfig.Overlay overlay = StorageConfig.overlay(config);
        mapper.readerForUpdating(overlay).readValue(configNode);
        try {
            configService.updateStorageConfig(overlay.build(), priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        // resize() doesn't do anything if the new and old value are the same
        cappedDatabase.resize(configService.getStorageConfig().getCappedDatabaseSizeMb() * 1024);
        return getStorage();
    }

    @POST("/backend/config/user-interface")
    String updateUserInterfaceConfig(String content, HttpResponse response) throws IOException,
            GeneralSecurityException, SQLException {
        logger.debug("updateUserInterfaceConfig(): content={}", content);
        // this code cannot be reached when httpServer is null
        checkNotNull(httpServer);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        String priorVersion = getAndRemoveVersionNode(configNode);
        UserInterfaceConfig config = configService.getUserInterfaceConfig();
        UserInterfaceConfig.Overlay overlay = UserInterfaceConfig.overlay(config);
        mapper.readerForUpdating(overlay).readValue(configNode);
        UserInterfaceConfig updatedConfig;
        try {
            updatedConfig = overlay.build();
        } catch (CurrentPasswordIncorrectException e) {
            return "{\"currentPasswordIncorrect\":true}";
        }
        try {
            configService.updateUserInterfaceConfig(updatedConfig, priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        // only create/delete session on successful update
        if (!config.isPasswordEnabled() && updatedConfig.isPasswordEnabled()) {
            httpSessionManager.createSession(response);
        } else if (config.isPasswordEnabled() && !updatedConfig.isPasswordEnabled()) {
            httpSessionManager.deleteSession(response);
        }
        // lastly deal with ui port change
        if (config.getPort() != updatedConfig.getPort()) {
            try {
                httpServer.changePort(updatedConfig.getPort());
                response.headers().set("Glowroot-Port-Changed", "true");
            } catch (PortChangeFailedException e) {
                logger.error(e.getMessage(), e);
                return getUserInterfaceWithPortChangeFailed();
            }
        }
        return getUserInterface();
    }

    @POST("/backend/config/advanced")
    String updateAdvancedConfig(String content) throws IOException, SQLException {
        logger.debug("updateAdvancedConfig(): content={}", content);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        String priorVersion = getAndRemoveVersionNode(configNode);
        AdvancedConfig config = configService.getAdvancedConfig();
        AdvancedConfig.Overlay overlay = AdvancedConfig.overlay(config);
        mapper.readerForUpdating(overlay).readValue(configNode);
        try {
            configService.updateAdvancedConfig(overlay.build(), priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        return getAdvanced();
    }

    @POST("/backend/config/plugin/(.+)")
    String updatePluginConfig(String pluginId, String content) throws IOException, SQLException {
        logger.debug("updatePluginConfig(): pluginId={}, content={}", pluginId, content);
        ObjectNode configNode = (ObjectNode) mapper.readTree(content);
        JsonNode versionNode = configNode.get("version");
        validateVersionNode(versionNode);
        String priorVersion = versionNode.asText();
        PluginConfig config = configService.getPluginConfig(pluginId);
        if (config == null) {
            throw new IllegalArgumentException("Plugin id '" + pluginId + "' not found");
        }
        PluginConfig.Builder builder = PluginConfig.builder(config);
        builder.overlay(configNode);
        try {
            configService.updatePluginConfig(builder.build(), priorVersion);
        } catch (OptimisticLockException e) {
            throw new JsonServiceException(PRECONDITION_FAILED, e);
        }
        return getPluginConfig(pluginId);
    }

    @RequiresNonNull("httpServer")
    private String getUserInterfaceWithPortChangeFailed() throws IOException {
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        ObjectWriter writer = mapper.writerWithView(UiView.class);
        jg.writeStartObject();
        writeUserInterface(jg, writer);
        jg.writeBooleanField("portChangeFailed", true);
        jg.writeEndObject();
        jg.close();
        return sb.toString();
    }

    @RequiresNonNull("httpServer")
    private void writeUserInterface(JsonGenerator jg, ObjectWriter writer) throws IOException {
        jg.writeFieldName("config");
        writer.writeValue(jg, configService.getUserInterfaceConfig());
        jg.writeNumberField("activePort", httpServer.getPort());
    }

    private String getAndRemoveVersionNode(ObjectNode configNode) {
        JsonNode versionNode = configNode.get("version");
        validateVersionNode(versionNode);
        configNode.remove("version");
        return versionNode.asText();
    }

    @EnsuresNonNull("#1")
    private void validateVersionNode(@Nullable JsonNode versionNode) {
        if (versionNode == null) {
            throw new JsonServiceException(BAD_REQUEST, "Version is missing");
        }
        if (!versionNode.isTextual()) {
            throw new JsonServiceException(BAD_REQUEST, "Version is not a string value");
        }
    }
}
