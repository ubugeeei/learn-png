package png

import png.PngError.InvalidImage

/** APNG animation-wide frame and play counts from acTL. Zero plays means infinite looping. */
final case class AnimationControl private (frames: Long, plays: Long)

object AnimationControl:
  private[png] def parse(data: Array[Byte]): Either[PngError, AnimationControl] =
    if data.length != 8 then Left(InvalidImage("acTL length must be 8"))
    else
      val cursor = Binary.Cursor(data)
      for
        frames <- cursor.uint32
        plays <- cursor.uint32
        _ <- Either.cond(frames > 0, (), InvalidImage("acTL frame count must be non-zero"))
      yield new AnimationControl(frames, plays)

/** How the next APNG frame affects the canvas after its display interval. */
enum DisposeOperation(val code: Int) derives CanEqual:
  case None extends DisposeOperation(0)
  case Background extends DisposeOperation(1)
  case Previous extends DisposeOperation(2)

/** How frame pixels combine with the existing APNG output buffer. */
enum BlendOperation(val code: Int) derives CanEqual:
  case Source extends BlendOperation(0)
  case Over extends BlendOperation(1)

/** Validated fcTL frame rectangle, timing, disposal, and blending fields. */
final case class FrameControl private (
    sequence: Long,
    width: Long,
    height: Long,
    xOffset: Long,
    yOffset: Long,
    delayNumerator: Int,
    delayDenominator: Int,
    dispose: DisposeOperation,
    blend: BlendOperation
):
  /** Frame duration in seconds; APNG defines a zero denominator as 100. */
  def delaySeconds: Double =
    delayNumerator.toDouble / (if delayDenominator == 0 then 100 else delayDenominator)

object FrameControl:
  private[png] def parse(
      data: Array[Byte],
      canvasWidth: Int,
      canvasHeight: Int
  ): Either[PngError, FrameControl] =
    if data.length != 26 then Left(InvalidImage("fcTL length must be 26"))
    else
      val cursor = Binary.Cursor(data)
      for
        sequence <- cursor.uint32
        width <- cursor.uint32
        height <- cursor.uint32
        x <- cursor.uint32
        y <- cursor.uint32
        numerator <- uint16(cursor)
        denominator <- uint16(cursor)
        disposeCode <- cursor.uint8
        blendCode <- cursor.uint8
        dispose <- DisposeOperation.values
          .find(_.code == disposeCode)
          .toRight(InvalidImage(s"unknown APNG dispose operation $disposeCode"))
        blend <- BlendOperation.values
          .find(_.code == blendCode)
          .toRight(InvalidImage(s"unknown APNG blend operation $blendCode"))
        _ <- Either.cond(width > 0 && height > 0, (), InvalidImage("APNG frame dimensions must be non-zero"))
        _ <- Either.cond(
          x + width <= canvasWidth && y + height <= canvasHeight,
          (),
          InvalidImage("APNG frame rectangle exceeds the canvas")
        )
      yield new FrameControl(sequence, width, height, x, y, numerator, denominator, dispose, blend)

  private def uint16(cursor: Binary.Cursor): Either[PngError, Int] =
    cursor.take(2).map(bytes => ((bytes(0) & 0xff) << 8) | (bytes(1) & 0xff))

/** Sequence number and compressed frame bytes carried by fdAT. */
final case class FrameData private (sequence: Long, compressed: Vector[Byte])

object FrameData:
  private[png] def parse(data: Array[Byte]): Either[PngError, FrameData] =
    if data.length < 4 then Left(InvalidImage("fdAT must contain a sequence number"))
    else Binary.Cursor(data).uint32.map(sequence => new FrameData(sequence, data.drop(4).toVector))
