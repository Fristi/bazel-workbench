package vectos.api

import sttp.tapir.ztapir.*
import sttp.tapir.generic.auto.* // For automatic derivation of schemas for case classes
import sttp.tapir.json.zio.*    // For ZIO JSON integration
import sttp.tapir.generic.auto.*
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.model.StatusCode
import sttp.tapir.typelevel.MatchType
import org.http4s.HttpRoutes
import zio.{Task, ZIO, URLayer, ZLayer}
import java.util.UUID
import vectos.domain.User
import vectos.service.{UserService, UserServiceError}

class UserRoutes(userService: UserService) {

  // Define base endpoint for /users path
  private val baseUserEndpoint = endpoint
    .tag("Users")
    .in("users")
    .errorOut(
      oneOf[UserServiceError](
        oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[UserServiceError.UserNotFound])),
        oneOfVariant(statusCode(StatusCode.Conflict).and(jsonBody[UserServiceError.EmailAlreadyExists])),
        oneOfVariant(statusCode(StatusCode.Conflict).and(jsonBody[UserServiceError.InvalidUpdateOperation])),
        oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[UserServiceError.RepositoryError]))  
      )
    )

  // POST /users - Create User
  val createUserServerEndpoint: ZServerEndpoint[Any, Any] =
    baseUserEndpoint.post
      .summary("Create a new user")
      .in(jsonBody[User.Create].description("User data for creation"))
      .out(statusCode(StatusCode.Created).and(jsonBody[User.Public].description("Created user details")))
      .zServerLogic(userCreate => userService.createUser(userCreate))

  // GET /users/{id} - Get User by ID
  val getUserByIdServerEndpoint: ZServerEndpoint[Any, Any] =
    baseUserEndpoint.get
      .summary("Get a user by their ID")
      .in(path[UUID]("id").description("User ID"))
      .out(jsonBody[User.Public].description("User details"))
      .zServerLogic(id => userService.getUser(id))

  // PUT /users/{id} - Update User
  val updateUserServerEndpoint: ZServerEndpoint[Any, Any] =
    baseUserEndpoint.put
      .summary("Update an existing user")
      .in(path[UUID]("id").description("User ID"))
      .in(jsonBody[User.Update].description("User data for update"))
      .out(jsonBody[User.Public].description("Updated user details"))
      .zServerLogic { case (id, userUpdate) =>
        userService.updateUser(id, userUpdate)
      }

  // DELETE /users/{id} - Delete User
  val deleteUserServerEndpoint: ZServerEndpoint[Any, Any] =
    baseUserEndpoint.delete
      .summary("Delete a user by their ID")
      .in(path[UUID]("id").description("User ID"))
      .out(statusCode(StatusCode.NoContent).description("User deleted successfully"))
      .zServerLogic(id => userService.deleteUser(id))

  // Aggregate all server endpoints
  val endpoints: List[ZServerEndpoint[Any, Any]] =
    List(
      createUserServerEndpoint,
      getUserByIdServerEndpoint,
      updateUserServerEndpoint,
      deleteUserServerEndpoint
    )

  // Convert Tapir endpoints to Http4s Routes
  // The ZHttp4sServerInterpreter requires a ZIO environment (Any type for logic, R for server options)
  val httpRoutes: HttpRoutes[Task] =
    ZHttp4sServerInterpreter[Any]().from(endpoints).toRoutes // R is Any here
}

object UserRoutes {
  // ZIO Layer for UserRoutes, depends on UserService
  val layer: URLayer[UserService, UserRoutes] = ZLayer.fromFunction(new UserRoutes(_))

  // Helper to extract HttpRoutes[Task] from the UserRoutes layer for convenience in Main
  val httpRoutesLayer: URLayer[UserService, HttpRoutes[Task]] = layer.project(_.httpRoutes)
}
