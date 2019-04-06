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

import com.google.common.io.Resources;
import org.junit.Test;

import static com.google.common.base.Charsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentationDescriptorsTest {

    @Test
    public void shouldParseJson() throws IOException {
        // given
        URL url = InstrumentationDescriptors.class.getResource("/glowroot.instrumentation.json");
        String content = Resources.toString(url, ISO_8859_1);

        // when
        InstrumentationDescriptor descriptor = InstrumentationDescriptors.getGson()
                .fromJson(content, InstrumentationDescriptor.class);

        // then
        assertThat(descriptor.adviceConfigs()).hasSize(1);
    }
}