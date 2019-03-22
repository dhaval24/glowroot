/*
 * Copyright 2012-2019 the original author or authors.
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

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

@JsonSerialize
@Value.Immutable
public abstract class InstrumentationDescriptor {

    public abstract String id();
    public abstract String name();
    public abstract ImmutableList<PropertyDescriptor> properties();
    @JsonProperty("instrumentation")
    public abstract ImmutableList<CustomInstrumentationConfig> instrumentationConfigs();
    public abstract ImmutableList<String> classes();
    @Value.Default
    public boolean collocate() {
        return false;
    }

    @JsonIgnore
    public abstract @Nullable File jarFile();
}
