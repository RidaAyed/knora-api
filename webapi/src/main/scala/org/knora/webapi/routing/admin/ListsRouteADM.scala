/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

/**
 * Provides API routes that deal with lists.
 */

@Api(value = "lists", produces = "application/json")
@Path("/admin/lists")
class ListsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ListADMJsonProtocol {

    /* concatenate paths in the CORRECT order and return */
    override def knoraApiPath: Route = getLists ~ postList ~ getList ~
      putListInfo ~ postListChildNode ~ deleteList ~
      deleteListNode ~

    @ApiOperation(
        value = "Get all lists optionally filtered by project",
        nickname = "getlists",
        httpMethod = "GET",
        response = classOf[ListsGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** return all lists optionally filtered by project */
    def getLists: Route = path("admin" / "lists") {
        get {
            parameters("projectIri".?) { maybeProjectIri: Option[IRI] =>
                requestContext =>
                    val projectIri = stringFormatter.toOptionalIri(maybeProjectIri, throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri"))

                    val requestMessage: Future[ListsGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListsGetRequestADM(projectIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    @ApiOperation(
        value = "Add new list",
        nickname = "addList",
        httpMethod = "POST",
        response = classOf[ListGetResponseADM]
    )
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to create", required = true,
            dataTypeClass = classOf[CreateListApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** create a new list (root node) **/
    def postList: Route = path("admin" / "lists") {
        post {
            entity(as[CreateListApiRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage: Future[ListCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListCreateRequestADM(
                        createListRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    @Path("/{IRI}")
    @ApiOperation(
        value = "Get a list with all list nodes",
        nickname = "getList",
        httpMethod = "GET",
        response = classOf[ListGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** get a list with all list nodes */
    def getList: Route = path("admin" / "lists" / Segment) { iri =>
        get {
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListGetRequestADM(listIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }



    @Path("/{IRI}")
    @ApiOperation(
        value = "Add new child node",
        nickname = "addListChildNode",
        httpMethod = "POST",
        response = classOf[ListNodeInfoGetResponseADM]
    )
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"node\" to create", required = true,
            dataTypeClass = classOf[CreateChildNodeApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** create a new child node */
    def postListChildNode: Route = path("admin" / "lists" / Segment) { iri =>
        post {
            /* add node to existing list node. the existing list node can be either the root or a child */
            entity(as[CreateChildNodeApiRequestADM]) { apiRequest =>
                requestContext =>
                    val parentNodeIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListChildNodeCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListChildNodeCreateRequestADM(
                        parentNodeIri = parentNodeIri,
                        createChildNodeRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    @Path("/{IRI}/Info")
    @ApiOperation(
        value = "Get basic list information (without children)",
        nickname = "getListInfo",
        httpMethod = "GET",
        response = classOf[ListInfoGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** return basic information about a list (without children) */
    def getListInfo: Route = path("admin" / "lists" / Segment / "Info") { iri =>
        get {
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListInfoGetRequestADM(listIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    @Path("/{IRI}/Info")
    @ApiOperation(
        value = "Update basic list information",
        nickname = "putListInfo",
        httpMethod = "PUT",
        response = classOf[ListInfoGetResponseADM]
    )
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to update", required = true,
            dataTypeClass = classOf[ChangeListInfoApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** update existing list info */
    def putListInfo: Route = path("admin" / "lists" / Segment / "Info") { iri =>
        put {
            entity(as[ChangeListInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListInfoChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListInfoChangeRequestADM(
                        listIri = listIri,
                        changeListRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    /** return information about a single node (without children) */
    def getListNode: Route = path("admin" / "lists" / "nodes" / Segment) { iri =>
        get {
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListNodeInfoGetRequestADM(listIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /** update list node */
    def putListNode: Route = path("admin" / "lists" / "nodes" / Segment) { iri =>
        put {

            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** delete list/node which should also delete all children */
    def deleteList: Route = path("admin" / "lists" / Segment) { iri =>
        delete {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    def deleteListNode: Route = path("admin" / "lists" / "nodes" / Segment) { iri =>
        delete {
            /* update list node */
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }


}
