package vectos.domain

import zio.json.{DeriveJsonCodec, JsonCodec}
import java.util.UUID

// Main User entity for persistence
case class User(
    id: UUID,
    email: String,
    hashedPassword: String,
    salt: String,
    firstName: String,
    lastName: String
)

object User {
  // DTO for API responses (excluding sensitive data)
  case class Public(id: UUID, email: String, firstName: String, lastName: String)
  object Public {
    def fromUser(user: User): Public = Public(user.id, user.email, user.firstName, user.lastName)
    implicit val codec: JsonCodec[Public] = DeriveJsonCodec.gen[Public]
  }

  // DTO for creating a new user
  case class Create(email: String, password: String, firstName: String, lastName: String)
  object Create {
    implicit val codec: JsonCodec[Create] = DeriveJsonCodec.gen[Create]
  }

  // DTO for updating an existing user (all fields optional)
  // Note: Password updates are typically handled separately and more securely.
  // This DTO will not include password update for simplicity in this CRUD example.
  case class Update(email: Option[String], firstName: Option[String], lastName: Option[String])
  object Update {
    implicit val codec: JsonCodec[Update] = DeriveJsonCodec.gen[Update]
  }

  // Implicit codec for the main User entity, if needed for direct serialization (e.g., internal logs, not API)
  // For API, Public, Create, Update DTOs are preferred.
  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
}
