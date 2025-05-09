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

package org.apache.flink.table.operations;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.expressions.DefaultSqlFactory;
import org.apache.flink.table.expressions.SqlFactory;

import java.util.List;

/**
 * Base class for representing an operation structure behind a user-facing {@link Table} API.
 *
 * <p>It represents an operation that can be a node of a relational query. It has a schema, that can
 * be used to validate a {@link QueryOperation} applied on top of this one.
 */
@PublicEvolving
public interface QueryOperation extends Operation {

    /** Resolved schema of this operation. */
    ResolvedSchema getResolvedSchema();

    /**
     * Returns a SQL string that fully serializes this instance. The serialized string can be used
     * for storing the query in e.g. a {@link org.apache.flink.table.catalog.Catalog} as a view.
     *
     * @return detailed string for persisting in a catalog
     * @see Operation#asSummaryString()
     */
    default String asSerializableString() {
        return asSerializableString(DefaultSqlFactory.INSTANCE);
    }

    /**
     * Returns a SQL string that fully serializes this instance. The serialized string can be used
     * for storing the query in e.g. a {@link org.apache.flink.table.catalog.Catalog} as a view.
     *
     * @param sqlFactory can be used to customize the serialization to a SQL string
     * @return Flink SQL string for persisting in a catalog
     * @see Operation#asSummaryString()
     * @see EnvironmentSettings.Builder#withSqlFactory(SqlFactory)
     */
    default String asSerializableString(SqlFactory sqlFactory) {
        throw new UnsupportedOperationException(
                "QueryOperations are not string serializable for now.");
    }

    List<QueryOperation> getChildren();

    default <T> T accept(QueryOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
