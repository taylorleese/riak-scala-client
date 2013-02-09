/*
 * Copyright (C) 2012-2013 Age Mooij
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scalapenos.riak

import spray.http.ContentType
import com.github.nscala_time.time.Imports._

/*
TODO:

Add indexes to RiakValue
Allows converters to define their own indexes

How to detect whether indexes are available?

Make converters stackable/delegatable so you could for instance use the standard spray json converter AND add extra indexes
*/

final case class RiakValue(
  value: String,
  contentType: ContentType,
  vclock: VClock,
  etag: String,
  lastModified: DateTime
  // links: Seq[RiakLink]
  // meta: Seq[RiakMeta]
) {
  import scala.util._
  import converters._

  def as[T: RiakValueReader]: Try[T] = implicitly[RiakValueReader[T]].read(this)

  def mapValue(newValue: String, newContentType: ContentType): RiakValue = copy(value = newValue, contentType = newContentType)
  def mapValue(newValue: String): RiakValue = copy(value = newValue)

  def flatMapValue(other: RiakValue): RiakValue = copy(value = other.value, contentType = other.contentType)
  def flatMapValue[T: RiakValueWriter](newValue: T): RiakValue = {
    // TODO: Is there a more optimal (single-step) way? This creates too many temporary objects
    flatMapValue(implicitly[RiakValueWriter[T]].write(newValue))
  }

  // TODO: add more common manipulation functions
}

object RiakValue {
  import spray.http.HttpBody
  import spray.httpx.marshalling._

  // use the magnet pattern so we can have overloads that would break due to type-erasure

  def apply(value: String): RiakValue = {
    apply(value, VClock.NotSpecified)
  }

  def apply(value: String, vclock: VClock): RiakValue = {
    apply(value, ContentType.`text/plain`, vclock)
  }

  def apply(value: String, contentType: ContentType): RiakValue = {
    apply(value, contentType, VClock.NotSpecified)
  }

  def apply(value: String, contentType: ContentType, vclock: VClock): RiakValue = {
    apply(value, contentType, vclock, "", DateTime.now)
  }

  def apply(value: Array[Byte], contentType: ContentType, vclock: VClock, etag: String, lastModified: DateTime): RiakValue = {
    RiakValue(new String(value, contentType.charset.nioCharset), contentType, vclock, etag, lastModified)
  }

  def apply[T: RiakValueWriter](value: T): RiakValue = implicitly[RiakValueWriter[T]].write(value)
  def apply[T: RiakValueWriter](value: T, vclock: VClock): RiakValue = implicitly[RiakValueWriter[T]].write(value, vclock)

  /**
   * Spray Marshaller for turning RiakValue instances into HttpEntity instances so they can be sent to Riak.
   */
  implicit val RiakValueMarshaller: Marshaller[RiakValue] = new Marshaller[RiakValue] {
    def apply(riakValue: RiakValue, ctx: MarshallingContext) {
      ctx.marshalTo(HttpBody(riakValue.contentType, riakValue.value.getBytes(riakValue.contentType.charset.nioCharset)))
    }
  }
}