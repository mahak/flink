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

package org.apache.flink.api.common;

import org.apache.flink.annotation.Internal;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * Serializable class which is created when archiving the job. It can be used to display job
 * information on the web interface without having to keep the classloader around after job
 * completion.
 */
@Internal
public class ArchivedExecutionConfig implements Serializable {

    private static final long serialVersionUID = 2907040336948181163L;
    private final String restartStrategyDescription;
    private final int parallelism;
    private final int maxParallelism;
    private final boolean objectReuseEnabled;
    private final long periodicMaterializeIntervalMillis;
    private final Map<String, String> globalJobParameters;

    public ArchivedExecutionConfig(ExecutionConfig ec) {
        restartStrategyDescription =
                RestartStrategyDescriptionUtils.getRestartStrategyDescription(ec.toConfiguration());

        maxParallelism = ec.getMaxParallelism();
        parallelism = ec.getParallelism();
        objectReuseEnabled = ec.isObjectReuseEnabled();
        periodicMaterializeIntervalMillis = ec.getPeriodicMaterializeIntervalMillis();
        if (ec.getGlobalJobParameters() != null && ec.getGlobalJobParameters().toMap() != null) {
            globalJobParameters = ec.getGlobalJobParameters().toMap();
        } else {
            globalJobParameters = Collections.emptyMap();
        }
    }

    public ArchivedExecutionConfig(
            String restartStrategyDescription,
            int maxParallelism,
            int parallelism,
            boolean objectReuseEnabled,
            long periodicMaterializeIntervalMillis,
            Map<String, String> globalJobParameters) {
        this.restartStrategyDescription = restartStrategyDescription;
        this.maxParallelism = maxParallelism;
        this.parallelism = parallelism;
        this.objectReuseEnabled = objectReuseEnabled;
        this.periodicMaterializeIntervalMillis = periodicMaterializeIntervalMillis;
        this.globalJobParameters = globalJobParameters;
    }

    public String getRestartStrategyDescription() {
        return restartStrategyDescription;
    }

    public int getMaxParallelism() {
        return maxParallelism;
    }

    public int getParallelism() {
        return parallelism;
    }

    public boolean getObjectReuseEnabled() {
        return objectReuseEnabled;
    }

    public long getPeriodicMaterializeIntervalMillis() {
        return periodicMaterializeIntervalMillis;
    }

    public Map<String, String> getGlobalJobParameters() {
        return globalJobParameters;
    }
}
