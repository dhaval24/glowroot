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

import org.checkerframework.checker.nullness.qual.Nullable;

public class InstrumentationProperty {

    private @Nullable String instrumentationId;
    private @Nullable String propertyName;
    private @Nullable String defaultValue;
    private @Nullable String description;

    public @Nullable String getInstrumentationId() {
        return instrumentationId;
    }

    public void setInstrumentationId(String instrumentationId) {
        this.instrumentationId = instrumentationId;
    }

    public @Nullable String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public @Nullable String getDefault() {
        return defaultValue;
    }

    public void setDefault(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
