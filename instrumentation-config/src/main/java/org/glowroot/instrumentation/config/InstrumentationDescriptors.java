/*
 * Copyright 2019 the original author or authors.
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
package org.glowroot.instrumentation.config;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;

import org.glowroot.instrumentation.config.ImmutableCustomInstrumentationConfig;
import org.glowroot.instrumentation.config.ImmutableInstrumentationDescriptor;
import org.glowroot.instrumentation.config.ImmutablePropertyDescriptor;

import static com.google.common.base.Preconditions.checkNotNull;

public class InstrumentationDescriptors {

    private static final ObjectMapper mapper = ObjectMappers.create();

    static {
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(CustomInstrumentationConfig.class,
                ImmutableCustomInstrumentationConfig.class);
        module.addAbstractTypeMapping(PropertyDescriptor.class, ImmutablePropertyDescriptor.class);
        mapper.registerModule(module);
    }

    public static List<InstrumentationDescriptor> readInstrumentationList() throws IOException {
        URL url = InstrumentationDescriptors.class
                .getResource("/META-INF/glowroot.instrumentation-list.json");
        if (url == null) {
            return ImmutableList.of();
        } else {
            List<InstrumentationDescriptor> descriptors = mapper.readValue(url,
                    new TypeReference<List<ImmutableInstrumentationDescriptor>>() {});
            return checkNotNull(descriptors);
        }
    }

    public static ObjectMapper getMapper() {
        return mapper;
    }
}
