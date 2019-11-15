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

package io.projectglow.vcf

import java.io.InputStream

import htsjdk.samtools.ValidationStringency
import htsjdk.tribble.readers.{SynchronousLineReader, LineIteratorImpl => HtsjdkLineIteratorImpl}
import htsjdk.variant.vcf.{VCFCodec, VCFHeader}

import org.apache.spark.sql.catalyst.InternalRow

import io.projectglow.common.GlowLogging
import io.projectglow.transformers.pipe.{OutputFormatter, OutputFormatterFactory}

class VCFOutputFormatter(stringency: ValidationStringency)
    extends OutputFormatter
    with GlowLogging {

  override def makeIterator(stream: InputStream): Iterator[Any] = {
    val codec = new VCFCodec
    val lineIterator = new HtsjdkLineIteratorImpl(new SynchronousLineReader(stream))
    logger.warn("Making line iterator")
    if (!lineIterator.hasNext) {
      return Iterator.empty
    }

    logger.warn("About to read header")
    val header = codec.readActualHeader(lineIterator).asInstanceOf[VCFHeader]
    logger.warn("Read header")
    val schema = VCFSchemaInferrer.inferSchema(true, true, header)
    logger.warn("Schema is " + schema)
    val converter =
      new VariantContextToInternalRowConverter(header, schema, stringency)

    val internalRowIter: Iterator[InternalRow] = new Iterator[InternalRow] {
      private var nextRecord: InternalRow = _
      private def readNextVc(): Unit = {
        while (nextRecord == null && lineIterator.hasNext) {
          logger.warn("Reading VC")
          val decoded = codec.decode(lineIterator.next())
          logger.warn("Decoded VC")
          if (decoded != null) {
            logger.warn("Set next VC")
            nextRecord = converter.convertRow(decoded, isSplit = false).copy()
          }
        }
      }

      override def hasNext: Boolean = {
        readNextVc()
        logger.warn("hasNext")
        nextRecord != null
      }

      override def next(): InternalRow = {
        if (hasNext) {
          logger.warn("next")
          val ret = nextRecord
          nextRecord = null
          ret
        } else {
          throw new NoSuchElementException("Iterator is empty")
        }
      }
    }
    Iterator(schema) ++ internalRowIter
  }
}

class VCFOutputFormatterFactory extends OutputFormatterFactory {
  override def name: String = "vcf"

  override def makeOutputFormatter(options: Map[String, String]): OutputFormatter = {
    val stringency = VCFOptionParser.getValidationStringency(options)
    new VCFOutputFormatter(stringency)
  }
}
