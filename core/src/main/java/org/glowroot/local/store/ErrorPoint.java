/*
 * Copyright 2014 the original author or authors.
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
package org.glowroot.local.store;

import org.glowroot.markers.UsedByJsonBinding;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@UsedByJsonBinding
public class ErrorPoint {

    public static final long UNKNOWN_TRANSACTION_COUNT = -1;

    private final long captureTime;
    private final long errorCount;
    private long transactionCount;

    ErrorPoint(long captureTime, long errorCount, long transactionCount) {
        this.captureTime = captureTime;
        this.errorCount = errorCount;
        this.transactionCount = transactionCount;
    }

    ErrorPoint(long captureTime, long errorCount) {
        this.captureTime = captureTime;
        this.errorCount = errorCount;
        this.transactionCount = UNKNOWN_TRANSACTION_COUNT;
    }

    public long getCaptureTime() {
        return captureTime;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(long transactionCount) {
        this.transactionCount = transactionCount;
    }
}
