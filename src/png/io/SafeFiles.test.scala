package png

import java.nio.file.Files
import scala.jdk.CollectionConverters.*
import munit.FunSuite

final class SafeFilesSuite extends FunSuite:
  test("successful writes replace the destination and leave no temporary sibling"):
    val directory = Files.createTempDirectory("learn-png-safe-write")
    val destination = directory.resolve("image.png")
    try
      Files.write(destination, Array[Byte](9, 9, 9))
      assertEquals(SafeFiles.write(destination, Array[Byte](1, 2, 3)), Right(destination))
      assertEquals(Files.readAllBytes(destination).toVector, Vector[Byte](1, 2, 3))
      val entries = Files.list(directory)
      try assertEquals(entries.iterator().asScala.size, 1)
      finally entries.close()
    finally
      Files.deleteIfExists(destination): Unit
      Files.deleteIfExists(directory): Unit

  test("missing parent directories are created"):
    val root = Files.createTempDirectory("learn-png-create-parent")
    val destination = root.resolve("nested/image.png")
    try
      assertEquals(SafeFiles.write(destination, Array[Byte](1)), Right(destination))
      assert(Files.exists(destination))
    finally
      Files.deleteIfExists(destination): Unit
      Files.deleteIfExists(destination.getParent): Unit
      Files.deleteIfExists(root): Unit
