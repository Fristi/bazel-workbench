package vectos.service

import zio.{Task, ZIO, ZLayer, IO}
import zio.json.*
import java.util.UUID
import vectos.domain.User
import vectos.repository.UserRepository
import vectos.security.PasswordService
import zio.json.JsonDecoder
import io.circe.Json

// Custom error type for service layer
sealed trait UserServiceError

object UserServiceError:
  case class UserNotFound(id: UUID) extends UserServiceError derives JsonCodec 
  case class EmailAlreadyExists(email: String) extends UserServiceError derives JsonCodec 
  case class InvalidUpdateOperation(message: String) extends UserServiceError derives JsonCodec 
  case class RepositoryError(cause: String) extends UserServiceError derives JsonCodec 

trait UserService {
  def createUser(createDto: User.Create): ZIO[Any, UserServiceError, User.Public]
  def getUser(id: UUID): ZIO[Any, UserServiceError, User.Public] // Changed to return User.Public directly or fail
  def updateUser(id: UUID, updateDto: User.Update): ZIO[Any, UserServiceError, User.Public]
  def deleteUser(id: UUID): ZIO[Any, UserServiceError, Unit] // Returns Unit on success or fails
}

object UserService {
  // Accessor methods for ZIO environment
  def createUser(createDto: User.Create): ZIO[UserService, UserServiceError, User.Public] =
    ZIO.serviceWithZIO(_.createUser(createDto))

  def getUser(id: UUID): ZIO[UserService, UserServiceError, User.Public] =
    ZIO.serviceWithZIO(_.getUser(id))

  def updateUser(id: UUID, updateDto: User.Update): ZIO[UserService, UserServiceError, User.Public] =
    ZIO.serviceWithZIO(_.updateUser(id, updateDto))

  def deleteUser(id: UUID): ZIO[UserService, UserServiceError, Unit] =
    ZIO.serviceWithZIO(_.deleteUser(id))
}

case class UserServiceImpl(userRepo: UserRepository, passwordService: PasswordService) extends UserService {

  private def mapRepoError[R, A](zio: ZIO[R, Throwable, A]): ZIO[R, UserServiceError, A] =
    zio.mapError(err => UserServiceError.RepositoryError(err.getMessage()))

  override def createUser(createDto: User.Create): ZIO[Any, UserServiceError, User.Public] =
    for {
      _ <- mapRepoError(userRepo.findByEmail(createDto.email)).flatMap {
             case Some(_) => ZIO.fail(UserServiceError.EmailAlreadyExists(createDto.email))
             case None    => ZIO.unit
           }
      uuid <- ZIO.succeed(UUID.randomUUID())
      (hashedPassword, salt) <- mapRepoError(passwordService.hashPassword(createDto.password))
      user = User(uuid, createDto.email, hashedPassword, salt, createDto.firstName, createDto.lastName)
      createdUser <- mapRepoError(userRepo.create(user))
    } yield User.Public.fromUser(createdUser)

  override def getUser(id: UUID): ZIO[Any, UserServiceError, User.Public] =
    mapRepoError(userRepo.findById(id)).flatMap {
      case Some(user) => ZIO.succeed(User.Public.fromUser(user))
      case None       => ZIO.fail(UserServiceError.UserNotFound(id))
    }

  override def updateUser(id: UUID, updateDto: User.Update): ZIO[Any, UserServiceError, User.Public] =
    mapRepoError(userRepo.findById(id)).flatMap {
      case Some(existingUser) =>
        // Check if the new email (if provided) is already taken by another user
        val emailCheck = updateDto.email match {
          case Some(newEmail) if newEmail != existingUser.email =>
            mapRepoError(userRepo.findByEmail(newEmail)).flatMap {
              case Some(_) => ZIO.fail(UserServiceError.EmailAlreadyExists(newEmail))
              case None    => ZIO.unit
            }
          case _ => ZIO.unit
        }

        for {
          _ <- emailCheck
          updatedUserEntity = existingUser.copy(
            email = updateDto.email.getOrElse(existingUser.email),
            firstName = updateDto.firstName.getOrElse(existingUser.firstName),
            lastName = updateDto.lastName.getOrElse(existingUser.lastName)
            // Note: Password update is not handled here. It would require current password verification.
          )
          updateCount <- mapRepoError(userRepo.update(updatedUserEntity))
          result <- if (updateCount > 0) {
                      // Fetch the updated user to return its public representation
                      mapRepoError(userRepo.findById(id)).flatMap { // id is existingUser.id
                        case Some(refetchedUser) => ZIO.succeed(User.Public.fromUser(refetchedUser))
                        case None => ZIO.fail(UserServiceError.UserNotFound(id)) // Should not happen if update succeeded
                      }
                    } else {
                      // This case (updateCount == 0 for an existing user) might indicate a concurrent modification
                      // or that the data provided for update resulted in no actual change.
                      // For simplicity, we treat it as if the user was not found or no update occurred.
                      // A more robust implementation might differentiate.
                      ZIO.fail(UserServiceError.UserNotFound(id)) // Or a different error like InvalidUpdateOperation
                    }
        } yield result
      case None => ZIO.fail(UserServiceError.UserNotFound(id))
    }

  override def deleteUser(id: UUID): ZIO[Any, UserServiceError, Unit] =
    mapRepoError(userRepo.delete(id)).flatMap {
      case count if count > 0 => ZIO.unit
      case _                  => ZIO.fail(UserServiceError.UserNotFound(id))
    }
}

object UserServiceImpl {
  val layer: ZLayer[UserRepository & PasswordService, Nothing, UserService] =
    ZLayer.fromFunction(UserServiceImpl.apply _)
}
