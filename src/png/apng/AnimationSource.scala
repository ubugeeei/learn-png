package png

import png.PngError.InvalidImage

/** One uncomposited APNG source rectangle supplied to the encoder. */
final case class AnimationSourceFrame private (
    image: Image16,
    xOffset: Int,
    yOffset: Int,
    delayNumerator: Int,
    delayDenominator: Int,
    dispose: DisposeOperation,
    blend: BlendOperation
)

object AnimationSourceFrame:
  def apply(
      image: Image16,
      xOffset: Int = 0,
      yOffset: Int = 0,
      delayNumerator: Int = 1,
      delayDenominator: Int = 10,
      dispose: DisposeOperation = DisposeOperation.None,
      blend: BlendOperation = BlendOperation.Source
  ): Either[PngError, AnimationSourceFrame] =
    if xOffset < 0 || yOffset < 0 then Left(InvalidImage("APNG frame offsets must be non-negative"))
    else if delayNumerator < 0 || delayNumerator > 0xffff || delayDenominator < 0 || delayDenominator > 0xffff
    then Left(InvalidImage("APNG delay fields must fit uint16"))
    else
      Right(
        new AnimationSourceFrame(image, xOffset, yOffset, delayNumerator, delayDenominator, dispose, blend)
      )

/** Validated APNG encoder input. The first frame is also the static PNG fallback. */
final case class AnimationSource private (
    canvasWidth: Int,
    canvasHeight: Int,
    plays: Long,
    frames: Vector[AnimationSourceFrame]
)

object AnimationSource:
  def apply(
      canvasWidth: Int,
      canvasHeight: Int,
      plays: Long,
      frames: Vector[AnimationSourceFrame]
  ): Either[PngError, AnimationSource] =
    if canvasWidth <= 0 || canvasHeight <= 0 then
      Left(InvalidImage("APNG canvas dimensions must be positive"))
    else if plays < 0 || plays > 0xffffffffL then Left(InvalidImage("APNG play count must fit uint32"))
    else if frames.isEmpty then Left(InvalidImage("APNG requires at least one frame"))
    else if frames.head.image.width != canvasWidth || frames.head.image.height != canvasHeight ||
      frames.head.xOffset != 0 || frames.head.yOffset != 0
    then Left(InvalidImage("the first encoded APNG frame must cover the complete canvas"))
    else if frames.head.dispose == DisposeOperation.Previous then
      Left(InvalidImage("the first encoded APNG frame may not use PREVIOUS disposal"))
    else if frames.exists(frame =>
        frame.xOffset.toLong + frame.image.width > canvasWidth ||
          frame.yOffset.toLong + frame.image.height > canvasHeight
      )
    then Left(InvalidImage("APNG frame rectangle exceeds the canvas"))
    else Right(new AnimationSource(canvasWidth, canvasHeight, plays, frames))
