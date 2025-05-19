package vectos

import zio._

object Main extends ZIOAppDefault {
  def program = for {
    _ <- Console.printLine("Hello world from ZIO!")
  } yield ()

  def run = program
}