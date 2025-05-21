package vectos

import zio.*

object Main extends ZIOAppDefault {
  def program: Task[Unit] = for {
    _ <- Console.printLine("Hello world from ZIO!")
  } yield ()

  def run = program
}
