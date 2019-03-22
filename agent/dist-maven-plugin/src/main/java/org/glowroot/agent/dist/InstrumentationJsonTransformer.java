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
package org.glowroot.agent.dist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.config.CustomInstrumentationConfig;
import org.glowroot.instrumentation.config.ImmutableCustomInstrumentationConfig;
import org.glowroot.instrumentation.config.ImmutableInstrumentationDescriptor;
import org.glowroot.instrumentation.config.ImmutablePropertyDescriptor;
import org.glowroot.instrumentation.config.InstrumentationDescriptor;
import org.glowroot.instrumentation.config.ObjectMappers;
import org.glowroot.instrumentation.config.PropertyDescriptor;
import org.glowroot.instrumentation.config.PropertyValue;

import static com.google.common.base.Charsets.UTF_8;

class InstrumentationJsonTransformer {

    private final MavenProject project;
    private final InstrumentationProperty[] properties;

    InstrumentationJsonTransformer(MavenProject project, InstrumentationProperty[] properties) {
        this.project = project;
        this.properties = properties;
    }

    void execute() throws Exception {
        createArtifactJar(project.getDependencyArtifacts());
    }

    private void createArtifactJar(Set<Artifact> artifacts) throws Exception {
        validateOverridesForDuplicates();
        List<InstrumentationDescriptor> descriptors = getDescriptors(artifacts);
        for (InstrumentationProperty property : properties) {
            String instrumentationId = property.getInstrumentationId();
            if (instrumentationId == null || instrumentationId.isEmpty()) {
                throw new MojoExecutionException("Missing or empty <instrumentationId>");
            }
            InstrumentationDescriptor descriptor = getDescriptor(instrumentationId, descriptors);
            String propertyName = property.getPropertyName();
            if (propertyName == null || propertyName.length() == 0) {
                throw new MojoExecutionException("Missing or empty <propertyName>");
            }
            validatePropertyName(propertyName, descriptor);
        }
        String instrumentationListJson = transform(descriptors);
        File metaInfDir = new File(project.getBuild().getOutputDirectory(), "META-INF");
        File file = new File(metaInfDir, "glowroot.instrumentation-list.json");
        if (!metaInfDir.exists() && !metaInfDir.mkdirs()) {
            throw new IOException("Could not create directory: " + metaInfDir.getAbsolutePath());
        }
        Files.write(instrumentationListJson, file, UTF_8);
    }

    private static List<InstrumentationDescriptor> getDescriptors(Set<Artifact> artifacts)
            throws IOException {
        List<InstrumentationDescriptor> descriptors = Lists.newArrayList();
        for (Artifact artifact : artifacts) {
            String content = getInstrumentationJson(artifact);
            if (content == null) {
                continue;
            }
            descriptors.add(readValue(content));
        }
        return descriptors;
    }

    private static @Nullable String getInstrumentationJson(Artifact artifact) throws IOException {
        File artifactFile = artifact.getFile();
        if (!artifactFile.exists()) {
            return null;
        }
        if (artifactFile.isDirectory()) {
            File jsonFile = new File(artifactFile, "META-INF/glowroot.instrumentation.json");
            if (!jsonFile.exists()) {
                return null;
            }
            return Files.toString(jsonFile, UTF_8);
        }
        JarInputStream jarIn = new JarInputStream(new FileInputStream(artifact.getFile()));
        try {
            JarEntry jarEntry;
            while ((jarEntry = jarIn.getNextJarEntry()) != null) {
                String name = jarEntry.getName();
                if (jarEntry.isDirectory()) {
                    continue;
                }
                if (!name.equals("META-INF/glowroot.instrumentation.json")) {
                    continue;
                }
                InputStreamReader in = new InputStreamReader(jarIn, UTF_8);
                String content = CharStreams.toString(in);
                in.close();
                return content;
            }
            return null;
        } finally {
            jarIn.close();
        }
    }

    private void validateOverridesForDuplicates() throws MojoExecutionException {
        for (InstrumentationProperty property : properties) {
            for (InstrumentationProperty property2 : properties) {
                if (property != property2
                        && Objects.equal(property.getInstrumentationId(),
                                property2.getInstrumentationId())
                        && Objects.equal(property.getPropertyName(), property2.getPropertyName())) {
                    throw new MojoExecutionException("Found duplicate <instrumentationProperty>: "
                            + property.getInstrumentationId() + "/" + property.getPropertyName());
                }
            }
        }
    }

    private InstrumentationDescriptor getDescriptor(String instrumentationId,
            List<InstrumentationDescriptor> descriptors) throws MojoExecutionException {
        for (InstrumentationDescriptor descriptor : descriptors) {
            if (descriptor.id().equals(instrumentationId)) {
                return descriptor;
            }
        }
        throw new MojoExecutionException("Found <instrumentationId> that does not have a"
                + " corresponding dependency in the pom file: " + instrumentationId);
    }

    private void validatePropertyName(String propertyName, InstrumentationDescriptor descriptor)
            throws MojoExecutionException {
        for (PropertyDescriptor propertyDescriptor : descriptor.properties()) {
            if (propertyDescriptor.name().equals(propertyName)) {
                return;
            }
        }
        throw new MojoExecutionException("Found <propertyName> that does not have a corresponding"
                + " property defined in the instrumentation: " + descriptor.id() + "/"
                + propertyName);
    }

    private String transform(List<InstrumentationDescriptor> descriptors) throws Exception {
        List<InstrumentationDescriptor> updatedDescriptors = Lists.newArrayList();
        for (InstrumentationDescriptor descriptor : descriptors) {
            updatedDescriptors.add(ImmutableInstrumentationDescriptor.copyOf(descriptor)
                    .withProperties(getPropertyDescriptorsWithOverrides(descriptor)));
        }
        return writeValue(updatedDescriptors);
    }

    private List<PropertyDescriptor> getPropertyDescriptorsWithOverrides(
            InstrumentationDescriptor descriptor) throws MojoExecutionException {
        List<PropertyDescriptor> propertyDescriptors = Lists.newArrayList();
        for (PropertyDescriptor propertyDescriptor : descriptor.properties()) {
            InstrumentationProperty property =
                    getInstrumentationProperty(descriptor.id(), propertyDescriptor.name());
            if (property == null) {
                propertyDescriptors.add(propertyDescriptor);
                continue;
            }
            PropertyDescriptorOverlay overlay = new PropertyDescriptorOverlay(propertyDescriptor);
            String propertyDefault = property.getDefault();
            String propertyDescription = property.getDescription();
            if (propertyDefault != null) {
                overlay.setDefault(getDefaultFromText(propertyDefault, propertyDescriptor.type()));
            }
            if (propertyDescription != null) {
                overlay.setDescription(propertyDescription);
            }
            propertyDescriptors.add(overlay.build());
        }
        return propertyDescriptors;
    }

    private PropertyValue getDefaultFromText(String text, PropertyValue.PropertyType type)
            throws MojoExecutionException {
        switch (type) {
            case BOOLEAN:
                return new PropertyValue(Boolean.parseBoolean(text));
            case DOUBLE:
                return new PropertyValue(Double.parseDouble(text));
            case STRING:
                return new PropertyValue(text);
            default:
                throw new MojoExecutionException("Unexpected property type: " + type);
        }
    }

    private @Nullable InstrumentationProperty getInstrumentationProperty(String instrumentationId,
            String propertyName) {
        for (InstrumentationProperty property : properties) {
            if (instrumentationId.equals(property.getInstrumentationId())
                    && propertyName.equals(property.getPropertyName())) {
                return property;
            }
        }
        return null;
    }

    private static InstrumentationDescriptor readValue(String content) throws IOException {
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(CustomInstrumentationConfig.class,
                ImmutableCustomInstrumentationConfig.class);
        module.addAbstractTypeMapping(PropertyDescriptor.class, ImmutablePropertyDescriptor.class);
        ObjectMapper mapper = ObjectMappers.create(module);
        return mapper.readValue(content, ImmutableInstrumentationDescriptor.class);
    }

    private static String writeValue(List<InstrumentationDescriptor> descriptors)
            throws IOException {
        ObjectMapper mapper = ObjectMappers.create();
        StringBuilder sb = new StringBuilder();
        JsonGenerator jg = mapper.getFactory().createGenerator(CharStreams.asWriter(sb));
        try {
            jg.setPrettyPrinter(ObjectMappers.getPrettyPrinter());
            jg.writeStartArray();
            for (InstrumentationDescriptor descriptor : descriptors) {
                ObjectNode objectNode = mapper.valueToTree(descriptor);
                ObjectMappers.stripEmptyContainerNodes(objectNode);
                jg.writeTree(objectNode);
            }
            jg.writeEndArray();
        } finally {
            jg.close();
        }
        // newline is not required, just a personal preference
        sb.append(ObjectMappers.NEWLINE);
        return sb.toString();
    }

    private static class PropertyDescriptorOverlay {

        private final String name;
        private final String label;
        private final PropertyValue.PropertyType type;
        private @Nullable PropertyValue defaultValue;
        private String description;

        private PropertyDescriptorOverlay(PropertyDescriptor base) {
            name = base.name();
            type = base.type();
            label = base.label();
            defaultValue = base.defaultValue();
            description = base.description();
        }

        private void setDefault(PropertyValue defaultValue) {
            this.defaultValue = defaultValue;
        }

        private void setDescription(String description) {
            this.description = description;
        }

        private PropertyDescriptor build() {
            return ImmutablePropertyDescriptor.builder()
                    .name(name)
                    .label(label)
                    .type(type)
                    .defaultValue(defaultValue)
                    .description(description).build();
        }
    }
}
