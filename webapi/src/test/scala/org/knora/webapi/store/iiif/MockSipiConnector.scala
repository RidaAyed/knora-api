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

package org.knora.webapi.store.iiif

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import org.knora.webapi.exceptions.{BadRequestException, SipiException}
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v1.responder.valuemessages.StillImageFileValueV1
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings}
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Keep track of the temporary files that was written in the route
  * when submitting a multipart request
  */
object SourcePath {
    private var sourcePath: File = new File("") // for init

    def setSourcePath(path: File) = {
        sourcePath = path
    }

    def getSourcePath() = {
        sourcePath
    }
}

/**
  * Constants for [[MockSipiConnector]].
  */
object MockSipiConnector {

    /**
      * A request to [[MockSipiConnector]] with this filename will always cause the responder to simulate a Sipi
      * error.
      */
    val FAILURE_FILENAME: String = "failure.jp2"
}

/**
  * Takes the place of [[SipiConnector]] for tests without an actual Sipi server, by returning hard-coded responses
  * simulating responses from Sipi.
  */
class MockSipiConnector extends Actor with ActorLogging {

    implicit val system: ActorSystem = context.system
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    val settings = KnoraSettings(system)


    def receive = {
        case sipiResponderConversionFileRequest: SipiConversionFileRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionFileRequest), log)
        case sipiResponderConversionPathRequest: SipiConversionPathRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionPathRequest), log)
        case getFileMetadataRequestV2: GetFileMetadataRequestV2 => try2Message(sender(), getFileMetadataV2(getFileMetadataRequestV2), log)
        case moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2 => try2Message(sender(), moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2), log)
        case deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2 => try2Message(sender(), deleteTemporaryFileV2(deleteTemporaryFileRequestV2), log)
        case IIIFServiceGetStatus => future2Message(sender(), FastFuture.successful(IIIFServiceStatusOK), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Imitates the Sipi server by returning a [[SipiConversionResponseV1]] representing an image conversion request.
      *
      * @param conversionRequest the conversion request to be handled.
      * @return a [[SipiConversionResponseV1]] imitating the answer from Sipi.
      */
    private def imageConversionResponse(conversionRequest: SipiConversionRequestV1): Future[SipiConversionResponseV1] = {
        Future {
            val originalFilename = conversionRequest.originalFilename
            val originalMimeType: String = conversionRequest.originalMimeType

            // we expect original mimetype to be "image/jpeg"
            if (originalMimeType != "image/jpeg") throw BadRequestException("Wrong mimetype for jpg file")

            val fileValueV1 = StillImageFileValueV1(
                internalMimeType = "image/jp2",
                originalFilename = originalFilename,
                originalMimeType = Some(originalMimeType),
                projectShortcode = conversionRequest.projectShortcode,
                dimX = 800,
                dimY = 800,
                internalFilename = "full.jp2"
            )

            // Whenever Knora had to create a temporary file, store its path
            // the calling test context can then make sure that is has actually been deleted after the test is done
            // (on successful or failed conversion)
            conversionRequest match {
                case conversionPathRequest: SipiConversionPathRequestV1 =>
                    // store path to tmp file
                    SourcePath.setSourcePath(conversionPathRequest.source)
                case _ => () // params request only
            }

            SipiConversionResponseV1(fileValueV1, file_type = SipiConstants.FileType.IMAGE)
        }
    }

    private def getFileMetadataV2(getFileMetadataRequestV2: GetFileMetadataRequestV2): Try[GetFileMetadataResponseV2] =
        Success {
            GetFileMetadataResponseV2(
                originalFilename = Some("test2.tiff"),
                originalMimeType = Some("image/tiff"),
                internalMimeType = "image/jp2",
                width = Some(512),
                height = Some(256),
                numpages = None
            )
        }

    private def moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2): Try[SuccessResponseV2] = {
        if (moveTemporaryFileToPermanentStorageRequestV2.internalFilename == MockSipiConnector.FAILURE_FILENAME) {
            Failure(SipiException("Sipi failed to move file to permanent storage"))
        } else {
            Success(SuccessResponseV2("Moved file to permanent storage"))
        }
    }

    private def deleteTemporaryFileV2(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2): Try[SuccessResponseV2] = {
        if (deleteTemporaryFileRequestV2.internalFilename == MockSipiConnector.FAILURE_FILENAME) {
            Failure(SipiException("Sipi failed to delete temporary file"))
        } else {
            Success(SuccessResponseV2("Deleted temporary file"))
        }
    }
}
