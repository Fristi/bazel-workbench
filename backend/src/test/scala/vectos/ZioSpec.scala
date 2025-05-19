package vectos

import zio.*
import zio.test.*
import zio.test.junit.JUnitRunnableSpec

class ZioSpec extends JUnitRunnableSpec {
  def spec = suite("MySpec")(
    test("test") {
      for {
        _ <- Main.program
      } yield assertCompletes
    }
  )
}
