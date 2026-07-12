package png

import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import png.Binary.*
import png.PngError.*

/** A validated four-letter PNG chunk type.
  *
  * Letter case encodes four properties; see
  * [[https://www.w3.org/TR/png-3/#5Chunk-naming-conventions PNG §5.4]]. The third (reserved) letter must
  * currently be uppercase. Unknown critical chunks cannot safely be ignored by a decoder.
  */
opaque type ChunkType = String

object ChunkType:
  val IHDR: ChunkType = "IHDR"
  val PLTE: ChunkType = "PLTE"
  val IDAT: ChunkType = "IDAT"
  val IEND: ChunkType = "IEND"
  val tRNS: ChunkType = "tRNS"
  val gAMA: ChunkType = "gAMA"
  val sRGB: ChunkType = "sRGB"
  val pHYs: ChunkType = "pHYs"
  val tEXt: ChunkType = "tEXt"
  val zTXt: ChunkType = "zTXt"
  val iTXt: ChunkType = "iTXt"
  val eXIf: ChunkType = "eXIf"
  val tIME: ChunkType = "tIME"
  val cHRM: ChunkType = "cHRM"
  val iCCP: ChunkType = "iCCP"
  val sBIT: ChunkType = "sBIT"
  val cICP: ChunkType = "cICP"
  val mDCV: ChunkType = "mDCV"
  val cLLI: ChunkType = "cLLI"
  val bKGD: ChunkType = "bKGD"
  val hIST: ChunkType = "hIST"
  val sPLT: ChunkType = "sPLT"
  val acTL: ChunkType = "acTL"
  val fcTL: ChunkType = "fcTL"
  val fdAT: ChunkType = "fdAT"

  def fromString(value: String): Either[PngError, ChunkType] =
    if value.length != 4 then Left(InvalidChunkType("must contain exactly four letters"))
    else if !value.forall(character =>
        character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z'
      )
    then Left(InvalidChunkType("characters must be ASCII letters"))
    else if value(2).isLower then Left(InvalidChunkType("the reserved third letter must be uppercase"))
    else Right(value)

  private[png] def unsafe(value: String): ChunkType = value

  extension (self: ChunkType)
    def name: String = self
    def bytes: Array[Byte] = self.getBytes(StandardCharsets.US_ASCII)
    def isAncillary: Boolean = self.head.isLower
    def isPrivate: Boolean = self(1).isLower
    def isSafeToCopy: Boolean = self(3).isLower

/** One physical PNG chunk, with a validated type and immutable payload.
  *
  * The serialized layout and CRC coverage follow [[https://www.w3.org/TR/png-3/#5Chunk-layout PNG §5.3]].
  */
final class Chunk private (val chunkType: ChunkType, private val payload: Array[Byte]):
  def data: Array[Byte] = payload.clone()
  def length: Int = payload.length

  def bytes: Array[Byte] =
    val body = chunkType.bytes ++ payload
    payload.length.toLong.uint32Bytes ++ body ++ Chunk.crc(body).uint32Bytes

  override def equals(other: Any): Boolean =
    other match
      case that: Chunk =>
        chunkType == that.chunkType && java.util.Arrays.equals(payload, that.payload)
      case _ =>
        false

  override def hashCode(): Int = 31 * chunkType.hashCode + java.util.Arrays.hashCode(payload)
  override def toString: String = s"Chunk(${chunkType.name}, ${payload.length} bytes)"

object Chunk:
  private val MaxLength = Int.MaxValue.toLong

  def apply(chunkType: ChunkType, data: Array[Byte]): Either[PngError, Chunk] =
    if data.length.toLong > MaxLength then Left(InvalidChunkLength(chunkType.name, data.length))
    else Right(new Chunk(chunkType, data.clone()))

  def parse(cursor: Binary.Cursor): Either[PngError, Chunk] =
    for
      length <- cursor.uint32
      _ <- Either.cond(length <= MaxLength, (), InvalidChunkLength("unknown", length))
      rawType <- cursor.take(4)
      kind <- ChunkType.fromString(new String(rawType, StandardCharsets.US_ASCII))
      data <- cursor.take(length.toInt)
      expected <- cursor.uint32
      actual = crc(rawType ++ data)
      _ <- Either.cond(expected == actual, (), CrcMismatch(kind.name, expected, actual))
      chunk <- apply(kind, data)
    yield chunk

  private[png] def crc(bytes: Array[Byte]): Long =
    val crc = CRC32()
    crc.update(bytes)
    crc.getValue
