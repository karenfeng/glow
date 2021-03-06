/*
 * Copyright 2019 The Glow Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.projectglow.sql.util

import java.util

import org.apache.spark.sql.catalyst.util.GenericArrayData
import org.apache.spark.unsafe.types.UTF8String

/**
 * Functions to be called from inside expression codegen. Defining the functions here
 * can reduce the size of the generated bytecode.
 */
object CodegenUtils {
  def asciiCharSplit(str: UTF8String, split: UTF8String): GenericArrayData = {
    val output = new util.ArrayList[UTF8String]
    val byteBuffer = str.getByteBuffer
    val bytes = byteBuffer.array()
    var start = byteBuffer.position()
    val splitByteBuffer = split.getByteBuffer
    val c = splitByteBuffer.array()(splitByteBuffer.position())
    var i = start
    while (i < byteBuffer.limit()) {
      if (bytes(i) == c) {
        output.add(UTF8String.fromBytes(bytes, start, i - start))
        start = i + 1
      }
      i += 1
    }

    output.add(UTF8String.fromBytes(bytes, start, byteBuffer.limit() - start))
    new GenericArrayData(output.toArray.asInstanceOf[Array[Any]])
  }
}
