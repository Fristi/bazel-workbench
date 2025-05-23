package vectos.config

import zio.config.magnolia.*
import zio.{Config, ConfigProvider, ZIO, ZLayer}

case class DbConfig(host: String, port: Int, user: String, password: String, database: String, maxConnections: Int) derives Config
case class HttpConfig(host: String, port: Int) derives Config
case class AppConfig(db: DbConfig, http: HttpConfig) derives Config

object AppConfig {
    val live: ZLayer[Any, Throwable, AppConfig] = ZLayer {
        val configDesc = summon[Config[AppConfig]]
        val source     = ConfigProvider.fromEnv()

        source.load(configDesc)
    }
}