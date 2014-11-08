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

import io.undertow.util.StatusCodes;

import org.glowroot.jvm.OptionalService;
import org.glowroot.markers.Static;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Static
class OptionalJsonServices {

    private OptionalJsonServices() {}

    static <T extends /*@NonNull*/Object> T validateAvailability(
            OptionalService<T> optionalService) {
        T service = optionalService.getService();
        if (service == null) {
            throw new JsonServiceException(StatusCodes.NOT_IMPLEMENTED,
                    optionalService.getClass().getSimpleName() + " service is not available: "
                            + optionalService.getAvailability().getReason());
        }
        return service;
    }
}
