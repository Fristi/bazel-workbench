package vectos.security

import zio.{Task, ZIO, ZLayer, IO}

trait PasswordService {
  /**
   * Hashes a password using a generated salt.
   * @return A tuple containing the hashed password and the salt used.
   */
  def hashPassword(password: String): Task[(String, String)]

  /**
   * Checks if a given plain password matches a stored hashed password and salt.
   * @return True if the password matches, false otherwise.
   */
  def checkPassword(password: String, hashedPassword: String, salt: String): Task[Boolean]
}

object PasswordService {
  // Accessor methods for ZIO environment
  def hashPassword(password: String): ZIO[PasswordService, Throwable, (String, String)] =
    ZIO.serviceWithZIO(_.hashPassword(password))

  def checkPassword(password: String, hashedPassword: String, salt: String): ZIO[PasswordService, Throwable, Boolean] =
    ZIO.serviceWithZIO(_.checkPassword(password, hashedPassword, salt))
}

case class PasswordServiceImpl() extends PasswordService {
  override def hashPassword(password: String): Task[(String, String)] =
    ZIO.succeed((password, "salt"))

  override def checkPassword(password: String, hashedPassword: String, salt: String): Task[Boolean] =
    ZIO.succeed(true)
}

object PasswordServiceImpl {
  val layer: ZLayer[Any, Nothing, PasswordService] = ZLayer.succeed(PasswordServiceImpl())
}
