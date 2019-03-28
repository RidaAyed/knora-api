/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.store.sipimessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.SipiException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import spray.json._

/**
  * An abstract trait for messages that can be sent to the [[org.knora.webapi.store.iiif.IIIFManager]]
  */
sealed trait IIIFRequest

/**
  * An abstract trait for messages that can be sent to [[org.knora.webapi.store.iiif.SipiConnector]].
  */
sealed trait SipiRequest extends IIIFRequest {
    def requestingUser: UserADM
}

/**
  * Requests file metadata from Sipi. A successful response is a [[GetImageMetadataResponseV2]].
  *
  * @param fileUrl        the URL at which Sipi can serve the file.
  * @param requestingUser the user making the request.
  */
case class GetImageMetadataRequest(fileUrl: String,
                                   requestingUser: UserADM) extends SipiRequest


/**
  * Represents a response from Sipi providing metadata about an image file.
  *
  * @param originalFilename the image's original filename.
  * @param originalMimeType the image's original MIME type.
  * @param width            the image's width in pixels.
  * @param height           the image's height in pixels.
  */
case class GetImageMetadataResponseV2(originalFilename: String,
                                      originalMimeType: String,
                                      width: Int,
                                      height: Int) {
    if (originalFilename.isEmpty) {
        throw SipiException(s"Sipi returned an empty originalFilename")
    }

    if (originalMimeType.isEmpty) {
        throw SipiException(s"Sipi returned an empty originalMimeType")
    }
}

object GetImageMetadataResponseV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val getImageMetadataResponseV2Format: RootJsonFormat[GetImageMetadataResponseV2] = jsonFormat4(GetImageMetadataResponseV2)
}

/**
  * Asks Sipi to move a file from temporary to permanent storage.
  *
  * @param internalFilename the name of the file.
  * @param prefix           the prefix under which the file should be stored.
  * @param requestingUser   the user making the request.
  */
case class MoveTemporaryFileToPermanentStorageRequest(internalFilename: String,
                                                      prefix: String,
                                                      requestingUser: UserADM) extends SipiRequest

/**
  * Asks Sipi to delete a temporary file.
  *
  * @param internalFilename the name of the file.
  * @param requestingUser   the user making the request.
  */
case class DeleteTemporaryFileRequest(internalFilename: String,
                                      requestingUser: UserADM) extends SipiRequest


/**
  * Asks Sipi for a text file. Currently only for UTF8 encoded text files.
  *
  * @param fileUrl        the URL pointing to the file.
  * @param requestingUser the user making the request.
  */
case class SipiGetTextFileRequest(fileUrl: String,
                                  requestingUser: UserADM) extends SipiRequest

/**
  * Represents a response for [[SipiGetTextFileRequest]].
  * @param content the file content.
  */
case class SipiGetTextFileResponse(content: String)

