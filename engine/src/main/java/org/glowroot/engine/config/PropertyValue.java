/*
 * Copyright 2014-2019 the original author or authors.
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
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.checkerframework.checker.nullness.qual.Nullable;

public class PropertyValue {

    // can be boolean, @Nullable Double or @NonNull String
    private final @Nullable Object value;
    public PropertyValue(@Nullable Object value) {
        this.value = value;
    }

    public @Nullable Object value() {
        return value;
    }

    public enum PropertyType {
        STRING, BOOLEAN, DOUBLE, LIST
    }

    public static class PropertyValueTypeAdapter extends TypeAdapter<PropertyValue> {

        @Override
        public PropertyValue read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            switch (token) {
                case BOOLEAN:
                    return new PropertyValue(in.nextBoolean());
                case NUMBER:
                    return new PropertyValue(in.nextDouble());
                case STRING:
                    return new PropertyValue(in.nextString());
                case BEGIN_ARRAY:
                    List<String> list = Lists.newArrayList();
                    while (in.peek() != JsonToken.END_ARRAY) {
                        list.add(in.nextString());
                    }
                    in.endArray();
                    return new PropertyValue(list);
                default:
                    throw new AssertionError("Unexpected json type: " + token);
            }
        }

        @Override
        public void write(JsonWriter out, PropertyValue value) {
            throw new UnsupportedOperationException("This should not be needed");
        }
    }
}
