/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.api.operators.sort;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.base.IntSerializer;
import org.apache.flink.api.common.typeutils.base.StringSerializer;
import org.apache.flink.api.common.typeutils.base.array.BytePrimitiveArraySerializer;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.typeutils.runtime.TupleSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.checkpoint.CheckpointException;
import org.apache.flink.runtime.checkpoint.channel.ChannelStateWriter;
import org.apache.flink.runtime.event.WatermarkEvent;
import org.apache.flink.runtime.io.AvailabilityProvider;
import org.apache.flink.runtime.operators.testutils.DummyInvokable;
import org.apache.flink.runtime.operators.testutils.MockEnvironment;
import org.apache.flink.streaming.api.operators.BoundedMultiInput;
import org.apache.flink.streaming.api.operators.sort.MultiInputSortingDataInput.SelectableSortingInputs;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.io.DataInputStatus;
import org.apache.flink.streaming.runtime.io.MultipleInputSelectionHandler;
import org.apache.flink.streaming.runtime.io.PushingAsyncDataInput;
import org.apache.flink.streaming.runtime.io.StreamMultipleInputProcessor;
import org.apache.flink.streaming.runtime.io.StreamOneInputProcessor;
import org.apache.flink.streaming.runtime.io.StreamTaskInput;
import org.apache.flink.streaming.runtime.streamrecord.LatencyMarker;
import org.apache.flink.streaming.runtime.streamrecord.RecordAttributes;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.watermarkstatus.WatermarkStatus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Longer running IT tests for {@link SortingDataInput} and {@link MultiInputSortingDataInput}.
 *
 * @see SortingDataInputTest
 * @see MultiInputSortingDataInputsTest
 */
class LargeSortingDataInputITCase {
    @Test
    void intKeySorting() throws Exception {
        int numberOfRecords = 500_000;
        GeneratedRecordsDataInput input = new GeneratedRecordsDataInput(numberOfRecords, 0);
        KeySelector<Tuple3<Integer, String, byte[]>, Integer> keySelector = value -> value.f0;
        try (MockEnvironment environment = MockEnvironment.builder().build();
                SortingDataInput<Tuple3<Integer, String, byte[]>, Integer> sortingDataInput =
                        new SortingDataInput<>(
                                input,
                                GeneratedRecordsDataInput.SERIALIZER,
                                new IntSerializer(),
                                keySelector,
                                environment.getMemoryManager(),
                                environment.getIOManager(),
                                true,
                                1.0,
                                new Configuration(),
                                new DummyInvokable(),
                                new ExecutionConfig())) {
            DataInputStatus inputStatus;
            VerifyingOutput<Integer> output = new VerifyingOutput<>(keySelector);
            do {
                inputStatus = sortingDataInput.emitNext(output);
            } while (inputStatus != DataInputStatus.END_OF_INPUT);

            assertThat(output.getSeenRecords()).isEqualTo(numberOfRecords);
        }
    }

    @Test
    void stringKeySorting() throws Exception {
        int numberOfRecords = 500_000;
        GeneratedRecordsDataInput input = new GeneratedRecordsDataInput(numberOfRecords, 0);
        KeySelector<Tuple3<Integer, String, byte[]>, String> keySelector = value -> value.f1;
        try (MockEnvironment environment = MockEnvironment.builder().build();
                SortingDataInput<Tuple3<Integer, String, byte[]>, String> sortingDataInput =
                        new SortingDataInput<>(
                                input,
                                GeneratedRecordsDataInput.SERIALIZER,
                                new StringSerializer(),
                                keySelector,
                                environment.getMemoryManager(),
                                environment.getIOManager(),
                                true,
                                1.0,
                                new Configuration(),
                                new DummyInvokable(),
                                new ExecutionConfig())) {
            DataInputStatus inputStatus;
            VerifyingOutput<String> output = new VerifyingOutput<>(keySelector);
            do {
                inputStatus = sortingDataInput.emitNext(output);
            } while (inputStatus != DataInputStatus.END_OF_INPUT);

            assertThat(output.getSeenRecords()).isEqualTo(numberOfRecords);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void multiInputKeySorting() throws Exception {
        int numberOfRecords = 500_000;
        GeneratedRecordsDataInput input1 = new GeneratedRecordsDataInput(numberOfRecords, 0);
        GeneratedRecordsDataInput input2 = new GeneratedRecordsDataInput(numberOfRecords, 1);
        KeySelector<Tuple3<Integer, String, byte[]>, String> keySelector = value -> value.f1;
        try (MockEnvironment environment = MockEnvironment.builder().build()) {
            SelectableSortingInputs selectableSortingInputs =
                    MultiInputSortingDataInput.wrapInputs(
                            new DummyInvokable(),
                            new StreamTaskInput[] {input1, input2},
                            new KeySelector[] {keySelector, keySelector},
                            new TypeSerializer[] {
                                GeneratedRecordsDataInput.SERIALIZER,
                                GeneratedRecordsDataInput.SERIALIZER
                            },
                            new StringSerializer(),
                            new StreamTaskInput[0],
                            environment.getMemoryManager(),
                            environment.getIOManager(),
                            true,
                            1.0,
                            new Configuration(),
                            new ExecutionConfig());

            StreamTaskInput<?>[] sortingDataInputs = selectableSortingInputs.getSortedInputs();
            try (StreamTaskInput<Tuple3<Integer, String, byte[]>> sortedInput1 =
                            (StreamTaskInput<Tuple3<Integer, String, byte[]>>)
                                    sortingDataInputs[0];
                    StreamTaskInput<Tuple3<Integer, String, byte[]>> sortedInput2 =
                            (StreamTaskInput<Tuple3<Integer, String, byte[]>>)
                                    sortingDataInputs[1]) {

                VerifyingOutput<String> output = new VerifyingOutput<>(keySelector);
                StreamMultipleInputProcessor multiSortedProcessor =
                        new StreamMultipleInputProcessor(
                                new MultipleInputSelectionHandler(
                                        selectableSortingInputs.getInputSelectable(), 2),
                                new StreamOneInputProcessor[] {
                                    new StreamOneInputProcessor(
                                            sortedInput1, output, new DummyOperatorChain()),
                                    new StreamOneInputProcessor(
                                            sortedInput2, output, new DummyOperatorChain())
                                });
                DataInputStatus inputStatus;
                do {
                    inputStatus = multiSortedProcessor.processInput();
                } while (inputStatus != DataInputStatus.END_OF_INPUT);

                assertThat(output.getSeenRecords()).isEqualTo(numberOfRecords * 2);
            }
        }
    }

    /**
     * The idea of the tests here is to check that the keys are grouped together. Therefore there
     * should not be a situation were we see a key different from the key of the previous record,
     * but one that we've seen before.
     *
     * <p>This output verifies that invariant.
     */
    private static final class VerifyingOutput<E>
            implements PushingAsyncDataInput.DataOutput<Tuple3<Integer, String, byte[]>> {

        private final KeySelector<Tuple3<Integer, String, byte[]>, E> keySelector;
        private final Set<E> seenKeys = new LinkedHashSet<>();
        private E currentKey = null;
        private int seenRecords = 0;

        private VerifyingOutput(KeySelector<Tuple3<Integer, String, byte[]>, E> keySelector) {
            this.keySelector = keySelector;
        }

        @Override
        public void emitRecord(StreamRecord<Tuple3<Integer, String, byte[]>> streamRecord)
                throws Exception {
            this.seenRecords++;
            E incomingKey = keySelector.getKey(streamRecord.getValue());
            if (!Objects.equals(incomingKey, currentKey)) {
                assertThat(seenKeys.add(incomingKey))
                        .as("Received an out of order key: " + incomingKey)
                        .isTrue();
                this.currentKey = incomingKey;
            }
        }

        @Override
        public void emitWatermark(Watermark watermark) throws Exception {}

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) throws Exception {}

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) throws Exception {}

        @Override
        public void emitRecordAttributes(RecordAttributes recordAttributes) throws Exception {}

        @Override
        public void emitWatermark(WatermarkEvent watermark) throws Exception {}

        public int getSeenRecords() {
            return seenRecords;
        }
    }

    private static final class GeneratedRecordsDataInput
            implements StreamTaskInput<Tuple3<Integer, String, byte[]>> {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static final TypeSerializer<Tuple3<Integer, String, byte[]>> SERIALIZER =
                new TupleSerializer<>(
                        (Class<Tuple3<Integer, String, byte[]>>) (Class) Tuple3.class,
                        new TypeSerializer[] {
                            new IntSerializer(),
                            new StringSerializer(),
                            new BytePrimitiveArraySerializer()
                        });

        private static final String ALPHA_NUM =
                "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        private final long numberOfRecords;
        private final int inputIdx;
        private int recordsGenerated;
        private final Random rnd = new Random();
        private final byte[] buffer;

        private GeneratedRecordsDataInput(int numberOfRecords, int inputIdx) {
            this.numberOfRecords = numberOfRecords;
            this.recordsGenerated = 0;
            this.buffer = new byte[500];
            rnd.nextBytes(buffer);
            this.inputIdx = inputIdx;
        }

        @Override
        public DataInputStatus emitNext(DataOutput<Tuple3<Integer, String, byte[]>> output)
                throws Exception {
            if (recordsGenerated == numberOfRecords) {
                recordsGenerated++;
                return DataInputStatus.END_OF_DATA;
            } else if (recordsGenerated > numberOfRecords) {
                return DataInputStatus.END_OF_INPUT;
            }

            output.emitRecord(
                    new StreamRecord<>(
                            Tuple3.of(rnd.nextInt(), randomString(rnd.nextInt(256)), buffer), 1));
            if (recordsGenerated++ == numberOfRecords) {
                return DataInputStatus.END_OF_DATA;
            } else {
                return DataInputStatus.MORE_AVAILABLE;
            }
        }

        @Override
        public CompletableFuture<?> getAvailableFuture() {
            return AvailabilityProvider.AVAILABLE;
        }

        private String randomString(int len) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(ALPHA_NUM.charAt(rnd.nextInt(ALPHA_NUM.length())));
            }
            return sb.toString();
        }

        @Override
        public int getInputIndex() {
            return inputIdx;
        }

        @Override
        public CompletableFuture<Void> prepareSnapshot(
                ChannelStateWriter channelStateWriter, long checkpointId)
                throws CheckpointException {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() throws IOException {}
    }

    private static class DummyOperatorChain implements BoundedMultiInput {
        @Override
        public void endInput(int inputId) throws Exception {}
    }
}
