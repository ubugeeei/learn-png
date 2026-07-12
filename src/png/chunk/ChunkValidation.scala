package png

import png.PngError.*

/** Cross-chunk invariants from [[https://www.w3.org/TR/png-3/#5ChunkOrdering PNG §5.6]] and
  * [[https://www.w3.org/TR/png-3/#11Ancillary-chunks PNG chapter 11]].
  *
  * Keeping datastream grammar beside [[Chunk]] makes validation reusable by the static and animated codecs
  * without mixing it into pixel reconstruction.
  */
private[png] object ChunkValidation:
  /** Validate required critical chunks, uniqueness, and relative positions. */
  def validate(chunks: Vector[Chunk]): Either[PngError, Unit] =
    val names = chunks.map(_.chunkType)
    val idatIndices = names.zipWithIndex.collect { case (ChunkType.IDAT, index) => index }
    val knownCritical = Set(ChunkType.IHDR, ChunkType.PLTE, ChunkType.IDAT, ChunkType.IEND)
    val unknownCritical =
      chunks.find(chunk => !chunk.chunkType.isAncillary && !knownCritical(chunk.chunkType))

    if names.headOption != Some(ChunkType.IHDR) then Left(InvalidChunkOrder("IHDR must be first"))
    else if names.count(_ == ChunkType.IHDR) != 1 then Left(InvalidChunkOrder("exactly one IHDR is required"))
    else if names.lastOption != Some(ChunkType.IEND) || names.count(_ == ChunkType.IEND) != 1 then
      Left(InvalidChunkOrder("IEND must occur exactly once and last"))
    else if chunks.last.length != 0 then Left(InvalidChunkLength("IEND", chunks.last.length))
    else if idatIndices.isEmpty then Left(InvalidChunkOrder("at least one IDAT is required"))
    else if idatIndices != (idatIndices.head to idatIndices.last).toVector then
      Left(InvalidChunkOrder("IDAT chunks must be consecutive"))
    else if names.count(_ == ChunkType.PLTE) > 1 then Left(InvalidChunkOrder("PLTE may occur at most once"))
    else if names.indexOf(ChunkType.PLTE) > idatIndices.head then
      Left(InvalidChunkOrder("PLTE must precede IDAT"))
    else
      unknownCritical.fold[Either[PngError, Unit]](Right(()))(chunk =>
        Left(UnsupportedFeature(s"critical chunk ${chunk.chunkType.name}"))
      )

  /** Validate color-type-specific ancillary constraints and singleton chunks. */
  def validateAncillary(chunks: Vector[Chunk], header: Header): Either[PngError, Unit] =
    val names = chunks.map(_.chunkType)
    val idat = names.indexOf(ChunkType.IDAT)
    val palette = names.indexOf(ChunkType.PLTE)
    val transparency = chunks.filter(_.chunkType == ChunkType.tRNS)
    val singleton = Vector(ChunkType.tRNS, ChunkType.gAMA, ChunkType.sRGB, ChunkType.pHYs)

    def occursBefore(kind: ChunkType, boundary: Int): Boolean =
      val index = names.indexOf(kind)
      index < 0 || index < boundary

    val colorBoundary = if palette >= 0 then palette else idat
    if singleton.exists(kind => names.count(_ == kind) > 1) then
      Left(InvalidChunkOrder("tRNS, gAMA, sRGB, and pHYs may occur at most once"))
    else if !occursBefore(ChunkType.gAMA, colorBoundary) || !occursBefore(ChunkType.sRGB, colorBoundary)
    then Left(InvalidChunkOrder("gAMA and sRGB must precede PLTE and IDAT"))
    else if !occursBefore(ChunkType.pHYs, idat) || !occursBefore(ChunkType.tRNS, idat) then
      Left(InvalidChunkOrder("pHYs and tRNS must precede IDAT"))
    else if transparency.nonEmpty && Set(ColorType.GrayscaleAlpha, ColorType.TruecolorAlpha)(header.colorType)
    then Left(InvalidImage(s"tRNS is forbidden for color type ${header.colorType.code}"))
    else
      transparency.headOption match
        case Some(chunk) if header.colorType == ColorType.Grayscale && chunk.length != 2 =>
          Left(InvalidChunkLength("tRNS", chunk.length))
        case Some(chunk) if header.colorType == ColorType.Truecolor && chunk.length != 6 =>
          Left(InvalidChunkLength("tRNS", chunk.length))
        case Some(chunk) if header.colorType == ColorType.Indexed && chunk.length == 0 =>
          Left(InvalidChunkLength("tRNS", chunk.length))
        case _ => Right(())
