package png

/** Small, reproducible throughput harness for the public PNG API.
  *
  * This is a regression investigation tool, not a substitute for JMH. It uses warm-up rounds, batches several
  * operations per sample, consumes results to discourage dead-code elimination, and reports both latency and
  * logical input throughput. Run it on an otherwise idle machine and compare multiple samples from the same
  * JVM and hardware.
  */
object PngBenchmark:
  final case class Config(warmups: Int, samples: Int, batchSize: Int, imageSize: Int)
  final case class Result(name: String, nanosecondsPerOperation: Double, mebibytesPerSecond: Double)

  private var consumed = 0L

  def main(arguments: Array[String]): Unit =
    val config = if arguments.contains("--quick") then Config(1, 2, 1, 64) else Config(5, 10, 4, 256)
    println("operation\tns/op\tMiB/s")
    run(config).foreach: result =>
      println(f"${result.name}\t${result.nanosecondsPerOperation}%.0f\t${result.mebibytesPerSecond}%.2f")

  private[png] def run(config: Config): Vector[Result] =
    require(config.warmups >= 0, "warmups must be non-negative")
    require(
      config.samples > 0 && config.batchSize > 0 && config.imageSize > 0,
      "benchmark sizes must be positive"
    )
    val image8 = generatedImage8(config.imageSize)
    val image16 = generatedImage16(config.imageSize)
    val png8 = Png.encode(image8).toOption.get
    val png16 = Png.encode16(image16).toOption.get
    val logical8 = image8.width.toLong * image8.height * 4
    val logical16 = image16.width.toLong * image16.height * 8

    val results = Vector(
      measure("decode-rgba8", logical8, config)(Png.decode(png8).fold(_.message.hashCode, checksum)),
      measure("decode-rgba16", logical16, config)(Png.decode16(png16).fold(_.message.hashCode, checksum)),
      measure("encode-rgba8", logical8, config)(Png.encode(image8).fold(_.message.hashCode, _.length)),
      measure("encode-rgba16", logical16, config)(Png.encode16(image16).fold(_.message.hashCode, _.length))
    )
    if consumed == Long.MinValue then throw IllegalStateException("unreachable benchmark checksum")
    results

  private def measure(name: String, logicalBytes: Long, config: Config)(operation: => Long): Result =
    def batch(): Unit = (0 until config.batchSize).foreach(_ => consumed ^= operation)
    (0 until config.warmups).foreach(_ => batch())
    val elapsed = Vector.tabulate(config.samples): _ =>
      val started = System.nanoTime()
      batch()
      System.nanoTime() - started
    val nanoseconds = elapsed.sum.toDouble / (config.samples.toLong * config.batchSize)
    val bytesPerSecond = logicalBytes / (nanoseconds / 1_000_000_000.0)
    Result(name, nanoseconds, bytesPerSecond / (1024 * 1024))

  private def generatedImage8(size: Int): Image = Image(
    size,
    size,
    Vector.tabulate(size * size): index =>
      Rgba(index & 0xff, index * 31 & 0xff, index * 73 & 0xff, 0xff - (index & 0x7f)).toOption.get
  ).toOption.get

  private def generatedImage16(size: Int): Image16 = Image16(
    size,
    size,
    Vector.tabulate(size * size): index =>
      Rgba16(index * 1009 & 0xffff, index * 4001 & 0xffff, index * 7919 & 0xffff, 0xffff - index).toOption.get
  ).toOption.get

  private def checksum(image: Image): Long =
    image.pixels.foldLeft(0L)((sum, pixel) => sum + pixel.red + pixel.green + pixel.blue + pixel.alpha)

  private def checksum(image: Image16): Long =
    image.pixels.foldLeft(0L)((sum, pixel) => sum + pixel.red + pixel.green + pixel.blue + pixel.alpha)
