package png

import java.nio.file.{ AtomicMoveNotSupportedException, Files, Path, StandardCopyOption, StandardOpenOption }
import scala.util.{ Try, Using }
import png.PngError.IoFailure

/** Transactional whole-file writes for encoded PNG datastreams.
  *
  * Bytes are forced to a temporary sibling before replacement. Atomic move is preferred, with a documented
  * same-file-system replacement fallback where atomic moves are unavailable.
  */
private[png] object SafeFiles:
  def write(path: Path, bytes: Array[Byte]): Either[PngError, Path] =
    Try:
      val absolute = path.toAbsolutePath
      val parent = Option(absolute.getParent).getOrElse(Path.of(".").toAbsolutePath)
      Files.createDirectories(parent)
      val temporary = Files.createTempFile(parent, s".${absolute.getFileName}.", ".tmp")
      try
        Using.resource(java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)): channel =>
          val buffer = java.nio.ByteBuffer.wrap(bytes)
          while buffer.hasRemaining do channel.write(buffer): Unit
          channel.force(true)
        try
          Files.move(
            temporary,
            absolute,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
          )
        catch
          case _: AtomicMoveNotSupportedException =>
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
        path
      finally Files.deleteIfExists(temporary): Unit
    .toEither.left.map(error =>
      IoFailure(
        s"atomically write $path",
        Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
      )
    )
