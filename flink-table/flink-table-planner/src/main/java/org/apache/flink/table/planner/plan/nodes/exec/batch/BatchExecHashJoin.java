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

package org.apache.flink.table.planner.plan.nodes.exec.batch;

import org.apache.flink.FlinkVersion;
import org.apache.flink.api.dag.Transformation;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.streaming.api.operators.SimpleOperatorFactory;
import org.apache.flink.streaming.api.operators.StreamOperatorFactory;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.planner.codegen.CodeGeneratorContext;
import org.apache.flink.table.planner.codegen.LongHashJoinGenerator;
import org.apache.flink.table.planner.codegen.ProjectionCodeGenerator;
import org.apache.flink.table.planner.delegation.PlannerBase;
import org.apache.flink.table.planner.plan.fusion.OpFusionCodegenSpecGenerator;
import org.apache.flink.table.planner.plan.fusion.generator.TwoInputOpFusionCodegenSpecGenerator;
import org.apache.flink.table.planner.plan.fusion.spec.HashJoinFusionCodegenSpec;
import org.apache.flink.table.planner.plan.nodes.exec.ExecEdge;
import org.apache.flink.table.planner.plan.nodes.exec.ExecNodeBase;
import org.apache.flink.table.planner.plan.nodes.exec.ExecNodeConfig;
import org.apache.flink.table.planner.plan.nodes.exec.ExecNodeContext;
import org.apache.flink.table.planner.plan.nodes.exec.ExecNodeMetadata;
import org.apache.flink.table.planner.plan.nodes.exec.InputProperty;
import org.apache.flink.table.planner.plan.nodes.exec.SingleTransformationTranslator;
import org.apache.flink.table.planner.plan.nodes.exec.spec.JoinSpec;
import org.apache.flink.table.planner.plan.nodes.exec.utils.ExecNodeUtil;
import org.apache.flink.table.planner.plan.utils.JoinUtil;
import org.apache.flink.table.planner.plan.utils.SorMergeJoinOperatorUtil;
import org.apache.flink.table.runtime.generated.GeneratedJoinCondition;
import org.apache.flink.table.runtime.generated.GeneratedProjection;
import org.apache.flink.table.runtime.operators.join.FlinkJoinType;
import org.apache.flink.table.runtime.operators.join.HashJoinOperator;
import org.apache.flink.table.runtime.operators.join.HashJoinType;
import org.apache.flink.table.runtime.operators.join.SortMergeJoinFunction;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** {@link BatchExecNode} for Hash Join. */
@ExecNodeMetadata(
        name = "batch-exec-join",
        version = 1,
        producedTransformations = BatchExecHashJoin.JOIN_TRANSFORMATION,
        consumedOptions = {
            "table.exec.resource.hash-join.memory",
            "table.exec.resource.external-buffer-memory",
            "table.exec.resource.sort.memory",
            "table.exec.spill-compression.enabled",
            "table.exec.spill-compression.block-size"
        },
        minPlanVersion = FlinkVersion.v2_0,
        minStateVersion = FlinkVersion.v2_0)
public class BatchExecHashJoin extends ExecNodeBase<RowData>
        implements BatchExecNode<RowData>, SingleTransformationTranslator<RowData> {

    public static final String JOIN_TRANSFORMATION = "join";
    public static final String FIELD_NAME_JOIN_SPEC = "joinSpec";
    public static final String FIELD_NAME_IS_BROADCAST = "isBroadcast";
    public static final String FIELD_NAME_LEFT_IS_BUILD = "leftIsBuild";
    public static final String FIELD_NAME_ESTIMATED_LEFT_AVG_ROW_SIZE = "estimatedLeftAvgRowSize";
    public static final String FIELD_NAME_ESTIMATED_RIGHT_AVG_ROW_SIZE = "estimatedRightAvgRowSize";
    public static final String FIELD_NAME_ESTIMATED_LEFT_ROW_COUNT = "estimatedLeftRowCount";
    public static final String FIELD_NAME_ESTIMATED_RIGHT_ROW_COUNT = "estimatedRightRowCount";
    public static final String FIELD_NAME_TRY_DISTINCT_BUILD_ROW = "tryDistinctBuildRow";

    @JsonProperty(FIELD_NAME_JOIN_SPEC)
    private final JoinSpec joinSpec;

    @JsonProperty(FIELD_NAME_IS_BROADCAST)
    private final boolean isBroadcast;

    @JsonProperty(FIELD_NAME_LEFT_IS_BUILD)
    private final boolean leftIsBuild;

    @JsonProperty(FIELD_NAME_ESTIMATED_LEFT_AVG_ROW_SIZE)
    private final int estimatedLeftAvgRowSize;

    @JsonProperty(FIELD_NAME_ESTIMATED_RIGHT_AVG_ROW_SIZE)
    private final int estimatedRightAvgRowSize;

    @JsonProperty(FIELD_NAME_ESTIMATED_LEFT_ROW_COUNT)
    private final long estimatedLeftRowCount;

    @JsonProperty(FIELD_NAME_ESTIMATED_RIGHT_ROW_COUNT)
    private final long estimatedRightRowCount;

    @JsonProperty(FIELD_NAME_TRY_DISTINCT_BUILD_ROW)
    private final boolean tryDistinctBuildRow;

    public BatchExecHashJoin(
            ReadableConfig tableConfig,
            JoinSpec joinSpec,
            int estimatedLeftAvgRowSize,
            int estimatedRightAvgRowSize,
            long estimatedLeftRowCount,
            long estimatedRightRowCount,
            boolean isBroadcast,
            boolean leftIsBuild,
            boolean tryDistinctBuildRow,
            InputProperty leftInputProperty,
            InputProperty rightInputProperty,
            RowType outputType,
            String description) {
        super(
                ExecNodeContext.newNodeId(),
                ExecNodeContext.newContext(BatchExecHashJoin.class),
                ExecNodeContext.newPersistedConfig(BatchExecHashJoin.class, tableConfig),
                Arrays.asList(leftInputProperty, rightInputProperty),
                outputType,
                description);
        this.joinSpec = checkNotNull(joinSpec);
        this.isBroadcast = isBroadcast;
        this.leftIsBuild = leftIsBuild;
        this.estimatedLeftAvgRowSize = estimatedLeftAvgRowSize;
        this.estimatedRightAvgRowSize = estimatedRightAvgRowSize;
        this.estimatedLeftRowCount = estimatedLeftRowCount;
        this.estimatedRightRowCount = estimatedRightRowCount;
        this.tryDistinctBuildRow = tryDistinctBuildRow;
    }

    @JsonCreator
    public BatchExecHashJoin(
            @JsonProperty(FIELD_NAME_ID) int id,
            @JsonProperty(FIELD_NAME_TYPE) ExecNodeContext context,
            @JsonProperty(FIELD_NAME_CONFIGURATION) ReadableConfig persistedConfig,
            @JsonProperty(FIELD_NAME_JOIN_SPEC) JoinSpec joinSpec,
            @JsonProperty(FIELD_NAME_ESTIMATED_LEFT_AVG_ROW_SIZE) int estimatedLeftAvgRowSize,
            @JsonProperty(FIELD_NAME_ESTIMATED_RIGHT_AVG_ROW_SIZE) int estimatedRightAvgRowSize,
            @JsonProperty(FIELD_NAME_ESTIMATED_LEFT_ROW_COUNT) long estimatedLeftRowCount,
            @JsonProperty(FIELD_NAME_ESTIMATED_RIGHT_ROW_COUNT) long estimatedRightRowCount,
            @JsonProperty(FIELD_NAME_IS_BROADCAST) boolean isBroadcast,
            @JsonProperty(FIELD_NAME_LEFT_IS_BUILD) boolean leftIsBuild,
            @JsonProperty(FIELD_NAME_TRY_DISTINCT_BUILD_ROW) boolean tryDistinctBuildRow,
            @JsonProperty(FIELD_NAME_INPUT_PROPERTIES) List<InputProperty> inputProperties,
            @JsonProperty(FIELD_NAME_OUTPUT_TYPE) RowType outputType,
            @JsonProperty(FIELD_NAME_DESCRIPTION) String description) {
        super(id, context, persistedConfig, inputProperties, outputType, description);
        checkArgument(inputProperties.size() == 2);
        this.joinSpec = checkNotNull(joinSpec);
        this.isBroadcast = isBroadcast;
        this.leftIsBuild = leftIsBuild;
        this.estimatedLeftAvgRowSize = estimatedLeftAvgRowSize;
        this.estimatedRightAvgRowSize = estimatedRightAvgRowSize;
        this.estimatedLeftRowCount = estimatedLeftRowCount;
        this.estimatedRightRowCount = estimatedRightRowCount;
        this.tryDistinctBuildRow = tryDistinctBuildRow;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Transformation<RowData> translateToPlanInternal(
            PlannerBase planner, ExecNodeConfig config) {
        ExecEdge leftInputEdge = getInputEdges().get(0);
        ExecEdge rightInputEdge = getInputEdges().get(1);

        Transformation<RowData> leftInputTransform =
                (Transformation<RowData>) leftInputEdge.translateToPlan(planner);
        Transformation<RowData> rightInputTransform =
                (Transformation<RowData>) rightInputEdge.translateToPlan(planner);
        // get input types
        RowType leftType = (RowType) leftInputEdge.getOutputType();
        RowType rightType = (RowType) rightInputEdge.getOutputType();

        JoinUtil.validateJoinSpec(joinSpec, leftType, rightType, false);

        int[] leftKeys = joinSpec.getLeftKeys();
        int[] rightKeys = joinSpec.getRightKeys();
        LogicalType[] keyFieldTypes =
                IntStream.of(leftKeys).mapToObj(leftType::getTypeAt).toArray(LogicalType[]::new);
        RowType keyType = RowType.of(keyFieldTypes);

        GeneratedJoinCondition condFunc =
                JoinUtil.generateConditionFunction(
                        config,
                        planner.getFlinkContext().getClassLoader(),
                        joinSpec.getNonEquiCondition().orElse(null),
                        leftType,
                        rightType);

        // projection for equals
        GeneratedProjection leftProj =
                ProjectionCodeGenerator.generateProjection(
                        new CodeGeneratorContext(
                                config, planner.getFlinkContext().getClassLoader()),
                        "HashJoinLeftProjection",
                        leftType,
                        keyType,
                        leftKeys);
        GeneratedProjection rightProj =
                ProjectionCodeGenerator.generateProjection(
                        new CodeGeneratorContext(
                                config, planner.getFlinkContext().getClassLoader()),
                        "HashJoinRightProjection",
                        rightType,
                        keyType,
                        rightKeys);

        Transformation<RowData> buildTransform;
        Transformation<RowData> probeTransform;
        GeneratedProjection buildProj;
        GeneratedProjection probeProj;
        int[] buildKeys;
        int[] probeKeys;
        RowType buildType;
        RowType probeType;
        int buildRowSize;
        long buildRowCount;
        long probeRowCount;
        boolean reverseJoin = !leftIsBuild;
        if (leftIsBuild) {
            buildTransform = leftInputTransform;
            buildProj = leftProj;
            buildType = leftType;
            buildRowSize = estimatedLeftAvgRowSize;
            buildRowCount = estimatedLeftRowCount;
            buildKeys = leftKeys;

            probeTransform = rightInputTransform;
            probeProj = rightProj;
            probeType = rightType;
            probeRowCount = estimatedRightRowCount;
            probeKeys = rightKeys;
        } else {
            buildTransform = rightInputTransform;
            buildProj = rightProj;
            buildType = rightType;
            buildRowSize = estimatedRightAvgRowSize;
            buildRowCount = estimatedRightRowCount;
            buildKeys = rightKeys;

            probeTransform = leftInputTransform;
            probeProj = leftProj;
            probeType = leftType;
            probeRowCount = estimatedLeftRowCount;
            probeKeys = leftKeys;
        }

        // operator
        StreamOperatorFactory<RowData> operator;
        FlinkJoinType joinType = joinSpec.getJoinType();
        HashJoinType hashJoinType =
                HashJoinType.of(
                        leftIsBuild,
                        joinType.isLeftOuter(),
                        joinType.isRightOuter(),
                        joinType == FlinkJoinType.SEMI,
                        joinType == FlinkJoinType.ANTI);

        long externalBufferMemory =
                config.get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_EXTERNAL_BUFFER_MEMORY)
                        .getBytes();
        long managedMemory = getLargeManagedMemory(joinType, config);

        // sort merge join function
        SortMergeJoinFunction sortMergeJoinFunction =
                SorMergeJoinOperatorUtil.getSortMergeJoinFunction(
                        planner.getFlinkContext().getClassLoader(),
                        config,
                        joinType,
                        leftType,
                        rightType,
                        leftKeys,
                        rightKeys,
                        keyType,
                        leftIsBuild,
                        joinSpec.getFilterNulls(),
                        condFunc,
                        1.0 * externalBufferMemory / managedMemory);

        boolean compressionEnabled =
                config.get(ExecutionConfigOptions.TABLE_EXEC_SPILL_COMPRESSION_ENABLED);
        int compressionBlockSize =
                (int)
                        config.get(ExecutionConfigOptions.TABLE_EXEC_SPILL_COMPRESSION_BLOCK_SIZE)
                                .getBytes();
        if (LongHashJoinGenerator.support(hashJoinType, keyType, joinSpec.getFilterNulls())) {
            operator =
                    LongHashJoinGenerator.gen(
                            config,
                            planner.getFlinkContext().getClassLoader(),
                            hashJoinType,
                            keyType,
                            buildType,
                            probeType,
                            buildKeys,
                            probeKeys,
                            buildRowSize,
                            buildRowCount,
                            reverseJoin,
                            condFunc,
                            leftIsBuild,
                            compressionEnabled,
                            compressionBlockSize,
                            sortMergeJoinFunction);
        } else {
            operator =
                    SimpleOperatorFactory.of(
                            HashJoinOperator.newHashJoinOperator(
                                    hashJoinType,
                                    leftIsBuild,
                                    compressionEnabled,
                                    compressionBlockSize,
                                    condFunc,
                                    reverseJoin,
                                    joinSpec.getFilterNulls(),
                                    buildProj,
                                    probeProj,
                                    tryDistinctBuildRow,
                                    buildRowSize,
                                    buildRowCount,
                                    probeRowCount,
                                    keyType,
                                    sortMergeJoinFunction));
        }

        return ExecNodeUtil.createTwoInputTransformation(
                buildTransform,
                probeTransform,
                createTransformationMeta(BatchExecHashJoin.JOIN_TRANSFORMATION, config),
                operator,
                InternalTypeInfo.of(getOutputType()),
                probeTransform.getParallelism(),
                managedMemory,
                false);
    }

    private long getLargeManagedMemory(FlinkJoinType joinType, ExecNodeConfig config) {
        long hashJoinManagedMemory =
                config.get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_HASH_JOIN_MEMORY).getBytes();

        // The memory used by SortMergeJoinIterator that buffer the matched rows, each side needs
        // this memory if it is full outer join
        long externalBufferMemory =
                config.get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_EXTERNAL_BUFFER_MEMORY)
                        .getBytes();
        // The memory used by BinaryExternalSorter for sort, the left and right side both need it
        long sortMemory =
                config.get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_SORT_MEMORY).getBytes();
        int externalBufferNum = 1;
        if (joinType == FlinkJoinType.FULL) {
            externalBufferNum = 2;
        }
        long sortMergeJoinManagedMemory = externalBufferMemory * externalBufferNum + sortMemory * 2;

        // Due to hash join maybe fallback to sort merge join, so here managed memory choose the
        // large one
        return Math.max(hashJoinManagedMemory, sortMergeJoinManagedMemory);
    }

    @Override
    public boolean supportFusionCodegen() {
        RowType leftType = (RowType) getInputEdges().get(0).getOutputType();
        LogicalType[] keyFieldTypes =
                IntStream.of(joinSpec.getLeftKeys())
                        .mapToObj(leftType::getTypeAt)
                        .toArray(LogicalType[]::new);
        RowType keyType = RowType.of(keyFieldTypes);
        FlinkJoinType joinType = joinSpec.getJoinType();
        HashJoinType hashJoinType =
                HashJoinType.of(
                        leftIsBuild,
                        joinType.isLeftOuter(),
                        joinType.isRightOuter(),
                        joinType == FlinkJoinType.SEMI,
                        joinType == FlinkJoinType.ANTI);
        // TODO decimal and multiKeys support and all HashJoinType support.
        return LongHashJoinGenerator.support(hashJoinType, keyType, joinSpec.getFilterNulls());
    }

    @Override
    protected OpFusionCodegenSpecGenerator translateToFusionCodegenSpecInternal(
            PlannerBase planner, ExecNodeConfig config, CodeGeneratorContext parentCtx) {
        OpFusionCodegenSpecGenerator leftInput =
                getInputEdges().get(0).translateToFusionCodegenSpec(planner, parentCtx);
        OpFusionCodegenSpecGenerator rightInput =
                getInputEdges().get(1).translateToFusionCodegenSpec(planner, parentCtx);
        boolean compressionEnabled =
                config.get(ExecutionConfigOptions.TABLE_EXEC_SPILL_COMPRESSION_ENABLED);
        int compressionBlockSize =
                (int)
                        config.get(ExecutionConfigOptions.TABLE_EXEC_SPILL_COMPRESSION_BLOCK_SIZE)
                                .getBytes();
        long managedMemory = getLargeManagedMemory(joinSpec.getJoinType(), config);
        OpFusionCodegenSpecGenerator hashJoinGenerator =
                new TwoInputOpFusionCodegenSpecGenerator(
                        leftInput,
                        rightInput,
                        managedMemory,
                        (RowType) getOutputType(),
                        new HashJoinFusionCodegenSpec(
                                new CodeGeneratorContext(
                                        config,
                                        planner.getFlinkContext().getClassLoader(),
                                        parentCtx),
                                isBroadcast,
                                leftIsBuild,
                                joinSpec,
                                estimatedLeftAvgRowSize,
                                estimatedRightAvgRowSize,
                                estimatedLeftRowCount,
                                estimatedRightRowCount,
                                compressionEnabled,
                                compressionBlockSize));
        leftInput.addOutput(1, hashJoinGenerator);
        rightInput.addOutput(2, hashJoinGenerator);
        return hashJoinGenerator;
    }
}
