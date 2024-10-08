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

package org.apache.flink.runtime.webmonitor.handlers;

import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.rest.messages.RequestBody;
import org.apache.flink.runtime.rest.messages.json.JobIDDeserializer;
import org.apache.flink.runtime.rest.messages.json.JobIDSerializer;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Base class for {@link RequestBody} for running a jar or querying the plan. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class JarRequestBody implements RequestBody {

    static final String FIELD_NAME_ENTRY_CLASS = "entryClass";
    static final String FIELD_NAME_PROGRAM_ARGUMENTS_LIST = "programArgsList";
    static final String FIELD_NAME_PARALLELISM = "parallelism";
    static final String FIELD_NAME_FLINK_CONFIGURATION = "flinkConfiguration";
    static final String FIELD_NAME_JOB_ID = "jobId";

    @JsonProperty(FIELD_NAME_ENTRY_CLASS)
    @Nullable
    private String entryClassName;

    @JsonProperty(FIELD_NAME_PROGRAM_ARGUMENTS_LIST)
    @Nullable
    private List<String> programArgumentsList;

    @JsonProperty(FIELD_NAME_PARALLELISM)
    @Nullable
    private Integer parallelism;

    @JsonProperty(FIELD_NAME_JOB_ID)
    @JsonDeserialize(using = JobIDDeserializer.class)
    @JsonSerialize(using = JobIDSerializer.class)
    @Nullable
    private JobID jobId;

    @JsonProperty(FIELD_NAME_FLINK_CONFIGURATION)
    @Nullable
    private Map<String, String> flinkConfiguration;

    JarRequestBody() {
        this(null, null, null, null, null);
    }

    @JsonCreator
    JarRequestBody(
            @Nullable @JsonProperty(FIELD_NAME_ENTRY_CLASS) String entryClassName,
            @Nullable @JsonProperty(FIELD_NAME_PROGRAM_ARGUMENTS_LIST)
                    List<String> programArgumentsList,
            @Nullable @JsonProperty(FIELD_NAME_PARALLELISM) Integer parallelism,
            @Nullable @JsonProperty(FIELD_NAME_JOB_ID) JobID jobId,
            @Nullable @JsonProperty(FIELD_NAME_FLINK_CONFIGURATION)
                    Map<String, String> flinkConfiguration) {
        this.entryClassName = entryClassName;
        this.programArgumentsList = programArgumentsList;
        this.parallelism = parallelism;
        this.jobId = jobId;
        this.flinkConfiguration = flinkConfiguration;
    }

    @Nullable
    @JsonIgnore
    public String getEntryClassName() {
        return entryClassName;
    }

    @Nullable
    @JsonIgnore
    public List<String> getProgramArgumentsList() {
        return programArgumentsList;
    }

    @Nullable
    @JsonIgnore
    public Integer getParallelism() {
        return parallelism;
    }

    @Nullable
    @JsonIgnore
    public JobID getJobId() {
        return jobId;
    }

    @JsonIgnore
    public Configuration getFlinkConfiguration() {
        return Optional.ofNullable(flinkConfiguration)
                .map(Configuration::fromMap)
                .orElse(new Configuration());
    }
}
