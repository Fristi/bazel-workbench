package vectos.repository

import cats.effect.kernel.Resource
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*
import zio.{Task, ZIO, ZLayer, IO}
import zio.interop.catz.* // For ZIO-Skunk interop with Task
import java.util.UUID
import vectos.domain.User
import natchez.Trace // Required for Skunk session operations

trait UserRepository {
  def create(user: User): Task[User]
  def findById(id: UUID): Task[Option[User]]
  def findByEmail(email: String): Task[Option[User]] // Added for uniqueness checks or login
  def update(user: User): Task[Int] // Returns number of affected rows
  def delete(id: UUID): Task[Int]   // Returns number of affected rows
}

object UserRepository {
  // Accessor methods for ZIO environment
  def create(user: User): ZIO[UserRepository, Throwable, User] = ZIO.serviceWithZIO(_.create(user))
  def findById(id: UUID): ZIO[UserRepository, Throwable, Option[User]] = ZIO.serviceWithZIO(_.findById(id))
  def findByEmail(email: String): ZIO[UserRepository, Throwable, Option[User]] = ZIO.serviceWithZIO(_.findByEmail(email))
  def update(user: User): ZIO[UserRepository, Throwable, Int] = ZIO.serviceWithZIO(_.update(user))
  def delete(id: UUID): ZIO[UserRepository, Throwable, Int] = ZIO.serviceWithZIO(_.delete(id))
}

// Ensure Trace[Task] is implicitly available for Skunk session.prepare.use calls
class UserRepositoryImpl(sessionPool: Resource[Task, Session[Task]]) extends UserRepository {

  implicit val trace: Trace[Task] = natchez.Trace.Implicits.noop

  private val userCodec: Codec[User] =
    (uuid ~ varchar(255) ~ varchar(255) ~ varchar(255) ~ varchar(100) ~ varchar(100)).gimap[User]

  private val insertUserCommand: Command[User] =
    sql"""
         INSERT INTO users (id, email, hashed_password, salt, first_name, last_name)
         VALUES ($userCodec)
       """.command

  private val selectUserByIdQuery: Query[UUID, User] =
    sql"""
         SELECT id, email, hashed_password, salt, first_name, last_name
         FROM users
         WHERE id = $uuid
       """.query(userCodec)

  private val selectUserByEmailQuery: Query[String, User] =
    sql"""
         SELECT id, email, hashed_password, salt, first_name, last_name
         FROM users
         WHERE email = $varchar
       """.query(userCodec)
  
  // For update, we map User to a tuple that matches the SQL parameters order.
  // id is used in WHERE clause.
  private val updateUserCommand: Command[(String, String, String, String, String, UUID)] =
    sql"""
         UPDATE users
         SET email = $varchar, hashed_password = $varchar, salt = $varchar, first_name = $varchar, last_name = $varchar
         WHERE id = $uuid
       """.command

  private val deleteUserCommand: Command[UUID] =
    sql"""
         DELETE FROM users WHERE id = $uuid
       """.command

  // Helper to use the session pool
  private def useSession[A](fa: Session[Task] => Task[A]): Task[A] =
    sessionPool.use(fa)

  override def create(user: User): Task[User] =
    useSession { s =>
      s.prepare(insertUserCommand).flatMap(_.execute(user))
    }.as(user) // Returns the user on successful insertion

  override def findById(id: UUID): Task[Option[User]] =
    useSession(_.prepare(selectUserByIdQuery).flatMap(_.option(id)))

  override def findByEmail(email: String): Task[Option[User]] =
    useSession(_.prepare(selectUserByEmailQuery).flatMap(_.option(email)))

  override def update(user: User): Task[Int] =
    useSession { s =>
      s.prepare(updateUserCommand).flatMap(
        _.execute((user.email, user.hashedPassword, user.salt, user.firstName, user.lastName, user.id))
      )
    }.map {
      case skunk.data.Completion.Update(count) => count
      case _                                   => 0 // Should not happen for UPDATE
    }

  override def delete(id: UUID): Task[Int] =
    useSession { s =>
      s.prepare(deleteUserCommand).flatMap(_.execute(id))
    }.map {
      case skunk.data.Completion.Delete(count) => count
      case _                                   => 0 // Should not happen for DELETE
    }
}

object UserRepositoryImpl {
  // Layer for UserRepositoryImpl, requires a Skunk Session Pool and a Trace[Task]
  // The Trace[Task] can be provided by natchez.Trace.Implicits.noop if no specific tracing is set up.
  val layer: ZLayer[Resource[Task, Session[Task]], Nothing, UserRepository] =
    ZLayer.fromFunction((pool: Resource[Task, Session[Task]]) => new UserRepositoryImpl(pool))
}
