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

package org.apache.flink.table.runtime.operators.deduplicate;

import org.apache.flink.api.common.functions.IterationRuntimeContext;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.runtime.generated.FilterCondition;
import org.apache.flink.table.runtime.generated.GeneratedFilterCondition;
import org.apache.flink.table.runtime.generated.GeneratedRecordEqualiser;
import org.apache.flink.table.runtime.generated.RecordEqualiser;
import org.apache.flink.table.runtime.keyselector.RowDataKeySelector;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.runtime.util.GenericRowRecordSortComparator;
import org.apache.flink.table.runtime.util.RowDataHarnessAssertor;
import org.apache.flink.table.runtime.util.RowDataRecordEqualiser;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.VarCharType;
import org.apache.flink.table.utils.HandwrittenSelectorUtil;

import java.time.Duration;

/** Base class of tests for all kinds of processing-time DeduplicateFunction. */
abstract class ProcTimeDeduplicateFunctionTestBase {

    Duration minTime = Duration.ofMillis(10);
    InternalTypeInfo<RowData> inputRowType =
            InternalTypeInfo.ofFields(VarCharType.STRING_TYPE, new BigIntType(), new IntType());

    int rowKeyIdx = 1;
    RowDataKeySelector rowKeySelector =
            HandwrittenSelectorUtil.getRowDataSelector(
                    new int[] {rowKeyIdx}, inputRowType.toRowFieldTypes());

    RowDataHarnessAssertor assertor =
            new RowDataHarnessAssertor(
                    inputRowType.toRowFieldTypes(),
                    new GenericRowRecordSortComparator(
                            rowKeyIdx, inputRowType.toRowFieldTypes()[rowKeyIdx]));

    static GeneratedRecordEqualiser generatedEqualiser =
            new GeneratedRecordEqualiser("", "", new Object[0]) {

                private static final long serialVersionUID = 1L;

                @Override
                public RecordEqualiser newInstance(ClassLoader classLoader) {
                    return new RowDataRecordEqualiser();
                }
            };

    static GeneratedFilterCondition generatedFilterCondition =
            new GeneratedFilterCondition("", "", new Object[0]) {
                @Override
                public FilterCondition newInstance(ClassLoader classLoader) {
                    return new TestingFilter();
                }
            };

    private static class TestingFilter implements FilterCondition {
        @Override
        public boolean apply(Context ctx, RowData input) {
            return input.getInt(2) > 10;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {}

        @Override
        public void close() throws Exception {}

        @Override
        public RuntimeContext getRuntimeContext() {
            return null;
        }

        @Override
        public IterationRuntimeContext getIterationRuntimeContext() {
            return null;
        }

        @Override
        public void setRuntimeContext(RuntimeContext t) {}
    }
}
