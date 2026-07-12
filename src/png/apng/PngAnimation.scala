package png

/** One fully composited APNG output frame and the control fields that produced it. */
final case class AnimationFrame(image: Image16, control: FrameControl)

/** A decoded APNG animation, including the ordinary PNG fallback image. */
final case class PngAnimation(
    canvasWidth: Int,
    canvasHeight: Int,
    plays: Long,
    frames: Vector[AnimationFrame],
    fallback: Image16,
    fallbackIsFirstFrame: Boolean
)
