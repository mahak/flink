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
package org.apache.flink.table.planner.plan.common

import org.apache.flink.table.catalog.{GenericInMemoryCatalog, ObjectIdentifier}
import org.apache.flink.table.legacy.factories.TableFactory
import org.apache.flink.table.planner.factories.utils.TestCollectionTableFactory
import org.apache.flink.table.planner.plan.utils.TestContextTableFactory
import org.apache.flink.table.planner.utils.TableTestBase
import org.apache.flink.testutils.junit.extensions.parameterized.{ParameterizedTestExtension, Parameters}

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, TestTemplate}
import org.junit.jupiter.api.extension.ExtendWith

import java.util.Optional

@ExtendWith(Array(classOf[ParameterizedTestExtension]))
class TableFactoryTest(isBatch: Boolean) extends TableTestBase {

  private val util = if (isBatch) batchTestUtil() else streamTestUtil()

  @BeforeEach
  def before(): Unit = {
    // we should clean the data to avoid serialization exception due to dirty data
    TestCollectionTableFactory.reset()
  }

  @TestTemplate
  def testTableSourceSinkFactory(): Unit = {
    val factory = new TestContextTableFactory(
      ObjectIdentifier.of("cat", "default", "t1"),
      ObjectIdentifier.of("cat", "default", "t2"),
      isBatch)
    util.tableEnv.getConfig.set(TestContextTableFactory.REQUIRED_KEY, Boolean.box(true))
    util.tableEnv.registerCatalog(
      "cat",
      new GenericInMemoryCatalog("default") {
        override def getTableFactory: Optional[TableFactory] = Optional.of(factory)
      })
    util.tableEnv.useCatalog("cat")

    val sourceDDL =
      """
        |create table t1(
        |  a int,
        |  b varchar,
        |  c as a + 1
        |) with (
        |  'connector.type' = 'filesystem',
        |  'connector.path' = '/to/my/path1',
        |  'format.type' = 'csv'
        |)
      """.stripMargin
    val sinkDDL =
      """
        |create table t2(
        |  a int,
        |  b as c - 1,
        |  c int
        |) with (
        |  'connector.type' = 'filesystem',
        |  'connector.path' = '/to/my/path2',
        |  'format.type' = 'csv'
        |)
      """.stripMargin
    val query =
      """
        |insert into t2
        |select t1.a, t1.c from t1
      """.stripMargin
    util.tableEnv.executeSql(sourceDDL)
    util.tableEnv.executeSql(sinkDDL)

    util.tableEnv.explainSql(query)
    assertThat(factory.hasInvokedSource).isTrue
    assertThat(factory.hasInvokedSink).isTrue
  }
}

object TableFactoryTest {
  @Parameters(name = "isBatch: {0}")
  def parameters(): java.util.Collection[Boolean] = {
    java.util.Arrays.asList(true, false)
  }
}
