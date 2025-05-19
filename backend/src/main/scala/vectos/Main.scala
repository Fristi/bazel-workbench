package vectos

import zio._

object Main extends ZIOAppDefault {
  def run = for {
    _ <- Console.printLine("Hello world from ZIO!")
  } yield ()
}