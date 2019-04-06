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
package org.glowroot.engine.config;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import org.glowroot.engine.config.PropertyValue.PropertyValueTypeAdapter;

import static com.google.common.base.Charsets.ISO_8859_1;
import static com.google.common.base.Preconditions.checkNotNull;

public class InstrumentationDescriptors {

    private static final Gson gson;

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        for (TypeAdapterFactory factory : ServiceLoader.load(TypeAdapterFactory.class)) {
            gsonBuilder.registerTypeAdapterFactory(factory);
        }
        gsonBuilder.registerTypeAdapter(PropertyValue.class, new PropertyValueTypeAdapter());
        gsonBuilder.registerTypeAdapterFactory(new EnumTypeAdapterFactory());
        gson = gsonBuilder.create();
    }

    public static List<InstrumentationDescriptor> readInstrumentationList() throws IOException {
        URL url = InstrumentationDescriptors.class
                .getResource("/META-INF/glowroot.instrumentation-list.json");
        if (url == null) {
            return ImmutableList.of();
        } else {
            String json = Resources.toString(url, ISO_8859_1);
            List<InstrumentationDescriptor> descriptors = gson.fromJson(json,
                    new TypeToken<List<ImmutableInstrumentationDescriptor>>() {}.getType());
            return checkNotNull(descriptors);
        }
    }

    public static Gson getGson() {
        return gson;
    }

    private static class EnumTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> /*@Nullable*/ TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            @SuppressWarnings("unchecked")
            Class<T> rawType = (Class<T>) type.getRawType();
            if (!rawType.isEnum()) {
                return null;
            }
            final Map<String, T> enumConstants = Maps.newHashMap();
            for (T enumConstant : rawType.getEnumConstants()) {
                enumConstants.put(((Enum<?>) enumConstant).name().replace('_', '-')
                        .toLowerCase(Locale.ENGLISH), enumConstant);
            }
            return new TypeAdapter<T>() {
                @Override
                public T read(JsonReader reader) throws IOException {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                        return null;
                    } else {
                        return enumConstants.get(reader.nextString());
                    }
                }
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    throw new UnsupportedOperationException("This should not be needed");
                }
            };
        }
    }
}