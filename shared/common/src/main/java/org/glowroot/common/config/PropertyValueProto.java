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
package org.glowroot.common.config;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.glowroot.instrumentation.config.PropertyValue;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig.InstrumentationProperty;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig.InstrumentationProperty.StringList;

import static com.google.common.base.Preconditions.checkNotNull;

public class PropertyValueProto {

    private PropertyValueProto() {}

    public static InstrumentationProperty.Value toProto(@Nullable Object value) {
        InstrumentationProperty.Value.Builder propertyValue =
                InstrumentationProperty.Value.newBuilder();
        if (value == null) {
            propertyValue.setDvalNull(true);
        } else if (value instanceof Boolean) {
            propertyValue.setBval((Boolean) value);
        } else if (value instanceof String) {
            propertyValue.setSval((String) value);
        } else if (value instanceof Double) {
            propertyValue.setDval((Double) value);
        } else if (value instanceof List) {
            StringList.Builder lval = StringList.newBuilder();
            for (Object v : (List<?>) value) {
                lval.addVal((String) checkNotNull(v));
            }
            propertyValue.setLval(lval);
        } else {
            throw new AssertionError(
                    "Unexpected property value type: " + value.getClass().getName());
        }
        return propertyValue.build();
    }

    public static PropertyValue create(InstrumentationProperty.Value value) {
        switch (value.getValCase()) {
            case BVAL:
                return new PropertyValue(value.getBval());
            case DVAL_NULL:
                return new PropertyValue(null);
            case DVAL:
                return new PropertyValue(value.getDval());
            case SVAL:
                return new PropertyValue(value.getSval());
            case LVAL:
                return new PropertyValue(ImmutableList.copyOf(value.getLval().getValList()));
            default:
                throw new IllegalStateException(
                        "Unexpected instrumentation property type: " + value.getValCase());
        }
    }
}
