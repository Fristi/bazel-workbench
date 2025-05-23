package vectos.api

import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.* // ZServerEndpoint is here
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import org.http4s.HttpRoutes
import zio.{Task, URLayer, ZLayer} // Task is zio.Task
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.swagger.SwaggerUIOptions

class SwaggerRoutes(userApiRoutes: UserRoutes) { // Depends on UserRoutes to get its endpoints

  // Interpret the user API endpoints as Swagger documentation endpoints
  private val swaggerApiEndpoints: List[ZServerEndpoint[Any, Any]] =
    SwaggerInterpreter().fromServerEndpoints[Task](userApiRoutes.endpoints, "Vectos User API", "1.0.0")
     // Provide Task as the F[_] type effect for server logic

  // Convert Swagger documentation endpoints to Http4s Routes
  val httpRoutes: HttpRoutes[Task] =
    ZHttp4sServerInterpreter[Any]().from(swaggerApiEndpoints).toRoutes
}

object SwaggerRoutes {
  // ZIO Layer for SwaggerRoutes, depends on UserRoutes
  val layer: URLayer[UserRoutes, SwaggerRoutes] = ZLayer.fromFunction(new SwaggerRoutes(_))

  // Helper to extract HttpRoutes[Task] from the SwaggerRoutes layer
  val httpRoutesLayer: URLayer[UserRoutes, HttpRoutes[Task]] = layer.project(_.httpRoutes)
}
