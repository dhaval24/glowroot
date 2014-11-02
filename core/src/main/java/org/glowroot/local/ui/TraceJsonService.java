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

import java.io.IOException;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.collector.Trace;
import org.glowroot.collector.TraceWriter;
import org.glowroot.markers.Singleton;

/**
 * Json service to read trace data, bound under /backend/trace/header.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@Singleton
@JsonService
class TraceJsonService {

    private static final Logger logger = LoggerFactory.getLogger(TraceJsonService.class);

    private final TraceCommonService traceCommonService;

    TraceJsonService(TraceCommonService traceCommonService) {
        this.traceCommonService = traceCommonService;
    }

    @GET("/backend/trace/header/(.+)")
    String getTrace(String id) throws IOException, SQLException {
        logger.debug("getTrace(): id={}", id);
        Trace trace = traceCommonService.getTrace(id);
        if (trace == null) {
            logger.debug("no trace found for id: {}", id);
            return "{\"expired\":true}";
        } else {
            return TraceWriter.toString(trace);
        }
    }
}
