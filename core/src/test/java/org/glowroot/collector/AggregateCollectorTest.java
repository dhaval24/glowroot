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
package org.glowroot.collector;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.glowroot.common.Clock;
import org.glowroot.transaction.model.Transaction;
import org.glowroot.transaction.model.TransactionMetricImpl;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class AggregateCollectorTest {

    @Test
    public void shouldFlushWithTrace() throws InterruptedException {
        // given
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Runnable runnable = (Runnable) invocation.getArguments()[0];
                runnable.run();
                return null;
            }
        }).when(scheduledExecutorService).execute(any(Runnable.class));
        MockAggregateRepository aggregateRepository = new MockAggregateRepository();
        AggregateCollector aggregateCollector = new AggregateCollector(
                scheduledExecutorService, aggregateRepository, Clock.systemClock(), 1);

        Transaction transaction = mock(Transaction.class);
        TransactionMetricImpl transactionMetric = mock(TransactionMetricImpl.class);
        when(transactionMetric.getName()).thenReturn("test 123");
        when(transaction.getDuration()).thenReturn(MILLISECONDS.toNanos(123));
        when(transaction.getRootMetric()).thenReturn(transactionMetric);
        // when
        int count = 0;
        long firstCaptureTime = aggregateCollector.add(transaction);
        long aggregateCaptureTime = (long) Math.ceil(firstCaptureTime / 1000.0) * 1000;
        while (true) {
            long captureTime = aggregateCollector.add(transaction);
            count++;
            if (captureTime > aggregateCaptureTime) {
                break;
            }
            Thread.sleep(1);
        }
        // then
        // aggregation is done in a separate thread, so give it a little time to complete
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) {
            if (aggregateRepository.getTotalMicros() > 0) {
                break;
            }
        }
        assertThat(aggregateRepository.getTotalMicros()).isEqualTo(count * 123 * 1000);
        aggregateCollector.close();
    }

    private static class MockAggregateRepository implements AggregateRepository {

        // volatile needed for visibility from other thread
        private volatile long totalMicros;

        @Override
        public void store(List<Aggregate> overallAggregates,
                List<Aggregate> transactionAggregates) {
            // only capture first non-zero value
            if (totalMicros == 0 && !overallAggregates.isEmpty()) {
                totalMicros = overallAggregates.get(0).getTotalMicros();
            }
        }

        private long getTotalMicros() {
            return totalMicros;
        }
    }
}
