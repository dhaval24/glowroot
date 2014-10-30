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
package org.glowroot.transaction;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.common.ScheduledRunnable;
import org.glowroot.markers.ThreadSafe;
import org.glowroot.transaction.model.Transaction;

/**
 * Scheduled to run as soon as the transaction exceeds a given threshold.
 * 
 * If the {@link Transaction} has already completed when this is run then it does nothing.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
class ImmediateTraceStoreRunnable extends ScheduledRunnable {

    private static final Logger logger = LoggerFactory.getLogger(ImmediateTraceStoreRunnable.class);

    private final Transaction transaction;
    private final TransactionCollector transactionCollector;
    private volatile boolean transactionPreviouslyCompleted;

    ImmediateTraceStoreRunnable(Transaction transaction,
            TransactionCollector transactionCollector) {
        this.transaction = transaction;
        this.transactionCollector = transactionCollector;
    }

    @Override
    public void runInternal() {
        logger.debug("run(): trace.id={}", transaction.getId());
        if (transaction.isCompleted()) {
            if (transactionPreviouslyCompleted) {
                // throw marker exception to terminate subsequent scheduled executions
                throw new TerminateSubsequentExecutionsException();
            } else {
                // there is a small window between trace completion and cancellation of this command
                // so give it one extra chance to be completed normally
                transactionPreviouslyCompleted = true;
                return;
            }
        }
        transaction.setPartial();
        transactionCollector.storePartialTrace(transaction);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("transaction", transaction)
                .add("transactionPreviouslyCompleted", transactionPreviouslyCompleted)
                .toString();
    }
}
