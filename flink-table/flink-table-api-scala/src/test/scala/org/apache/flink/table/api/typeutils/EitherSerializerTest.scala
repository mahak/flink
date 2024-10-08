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
package org.apache.flink.table.api.typeutils

import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.common.typeutils.SerializerTestBase
import org.apache.flink.api.common.typeutils.base.{IntSerializer, StringSerializer}
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Test suite for the [[EitherSerializer]] */
class EitherSerializerTest extends SerializerTestBase[Either[String, Integer]] {

  // --------------------------------------------------------------------------
  //  test suite
  // --------------------------------------------------------------------------

  override protected def createSerializer() =
    new EitherSerializer[String, Integer](StringSerializer.INSTANCE, IntSerializer.INSTANCE)

  override protected def getLength: Int = -1

  override protected def getTypeClass: Class[Either[String, Integer]] =
    classOf[Either[String, Integer]]

  override protected def getTestData: Array[Either[String, Integer]] =
    Array[Either[String, Integer]](
      Left("hello"),
      Right(17),
      Right(0),
      Left("friend"),
      Right(200),
      Right(100),
      Left("foo"),
      Right(1060876234),
      Left("bar")
    )

  // --------------------------------------------------------------------------
  //  either serializer specific tests
  // --------------------------------------------------------------------------

  @Test
  def testDuplication(): Unit = {
    val serializerSS: EitherSerializer[String, String] =
      new EitherSerializer[String, String](
        StringSerializer.INSTANCE,
        StringSerializer.INSTANCE
      )

    val serializerSO: EitherSerializer[String, Object] =
      new EitherSerializer[String, Object](
        StringSerializer.INSTANCE,
        new KryoSerializer[Object](classOf[Object], new SerializerConfigImpl())
      )

    val serializerOS: EitherSerializer[Object, String] =
      new EitherSerializer[Object, String](
        new KryoSerializer[Object](classOf[Object], new SerializerConfigImpl()),
        StringSerializer.INSTANCE
      )

    assertThat(serializerSS.duplicate).isSameAs(serializerSS)
    assertThat(serializerSO.duplicate).isNotSameAs(serializerSO)
    assertThat(serializerOS.duplicate).isNotSameAs(serializerOS)
  }
}
