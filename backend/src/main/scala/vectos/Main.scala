package vectos

import cats.effect.{Async => CatsAsync}
import cats.effect.std.{Console => CatsConsole}
import cats.effect.kernel.Resource
import zio.*
import zio.interop.catz.* // For Task to IO conversions and typeclass instances
import zio.interop.catz.implicits.* // For ZIOAppDefault syntax with CE3
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.HttpRoutes
import com.comcast.ip4s.* // For Host and Port types
import skunk.Session
import natchez.Trace // For Skunk session and repository
import natchez.noop.NoopTrace
import vectos.config.{AppConfig, DbConfig, HttpConfig}
import vectos.api.{UserRoutes, SwaggerRoutes}
import vectos.repository.{UserRepository, UserRepositoryImpl}
import vectos.service.{UserService, UserServiceImpl}
import vectos.security.{PasswordService, PasswordServiceImpl}
import fs2.io.net.Network

object Main extends ZIOAppDefault {

  // Skunk Session Pool Layer
  private val skunkSessionPoolLayer: ZLayer[DbConfig, Throwable, Resource[Task, Session[Task]]] =
    ZLayer.scoped {
      
      implicit val trace: Trace[Task] = natchez.Trace.Implicits.noop  // Replace with real trace if needed
      implicit val console: CatsConsole[Task] = CatsConsole.make[Task]

      for {
        dbConfig <- ZIO.service[DbConfig]
        session <- Session.pooled[Task](
          host     = dbConfig.host,
          port     = dbConfig.port,
          user     = dbConfig.user,
          password = Some(dbConfig.password).filter(_.nonEmpty),
          database = dbConfig.database,
          max      = dbConfig.maxConnections
        ).toScopedZIO
      } yield session
    }

  // Define the full application program
  private val serverProgram =
    for {
      httpConfig <- ZIO.service[HttpConfig]
      userApiRoutes <- ZIO.service[UserRoutes].map(_.httpRoutes) // Assuming UserRoutes provides its routes
      swaggerApiRoutes <- ZIO.service[SwaggerRoutes].map(_.httpRoutes) // Assuming SwaggerRoutes provides its routes

      allRoutes = Router(
        "/api" -> userApiRoutes,    // User API under /api
        "/" -> swaggerApiRoutes // Swagger UI at /docs (or / as per SwaggerRoutes config)
      ).orNotFound

      host <- ZIO.fromOption(Host.fromString(httpConfig.host)).orElseFail(new IllegalArgumentException(s"Invalid host: ${httpConfig.host}"))
      port <- ZIO.fromOption(Port.fromInt(httpConfig.port)).orElseFail(new IllegalArgumentException(s"Invalid port: ${httpConfig.port}"))

      _ <- Console.printLine(s"Server starting on http://${httpConfig.host}:${httpConfig.port}/docs (Swagger UI)")
      _ <- EmberServerBuilder
        .default[Task]
        .withHost(host)
        .withPort(port)
        .withHttpApp(allRoutes)
        .build
        .useForever
    } yield ()

  // Define all layers needed for the application
  private val appLayers: ZLayer[Any, Throwable, HttpConfig & UserRoutes & SwaggerRoutes] =
    ZLayer.make[HttpConfig & UserRoutes & SwaggerRoutes](
      skunkSessionPoolLayer,
      AppConfig.live.project(_.db),
      AppConfig.live.project(_.http),
      PasswordServiceImpl.layer,
      UserRepositoryImpl.layer,
      UserServiceImpl.layer,
      UserRoutes.layer,
      SwaggerRoutes.layer
    )


  // Entry point of the application
  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    serverProgram
      .provideLayer(appLayers)
      .tapError(err => Console.printLine(s"Application failed: $err").orDie)
      .exitCode
}
