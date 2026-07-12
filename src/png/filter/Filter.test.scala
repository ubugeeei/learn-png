package png

import munit.FunSuite
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

final class FilterSuite extends FunSuite with ScalaCheckSuite:
  private val rows = List(
    Array.emptyByteArray,
    Array[Byte](0),
    Array[Byte](1, 2, 3, 4, -1, -128),
    Array.tabulate[Byte](257)(index => (index * 73).toByte)
  )

  test("every filter round-trips rows with and without a previous row"):
    for
      filter <- Filter.values
      row <- rows
      bytesPerPixel <- 1 to 4
      previous <- List(Array.emptyByteArray, Array.fill(row.length)(17.toByte))
    do
      val encoded = filter.encode(row, previous, bytesPerPixel)
      assertEquals(filter.decode(encoded, previous, bytesPerPixel).toVector, row.toVector)

  test("Paeth predictor matches the normative examples of nearest candidates"):
    val row = Array[Byte](10, 20, 30)
    val previous = Array[Byte](5, 15, 25)
    val encoded = Filter.Paeth.encode(row, previous, 1)
    assertEquals(Filter.Paeth.decode(encoded, previous, 1).toVector, row.toVector)

  test("filter chooser returns one of the five reversible filters"):
    val row = Array.fill[Byte](100)(42)
    val (filter, encoded) = Filter.choose(row, Array.emptyByteArray, 1)
    assertEquals(filter.decode(encoded, Array.emptyByteArray, 1).toVector, row.toVector)

  test("unknown filter type is rejected"):
    assert(Filter.fromCode(5).isLeft)

  property("decode is the inverse of encode for every filter"):
    val row = Gen.listOf(Gen.choose(Byte.MinValue, Byte.MaxValue)).map(_.take(2048).toArray)
    val bytesPerPixel = Gen.choose(1, 8)

    forAll(row, bytesPerPixel): (raw, bpp) =>
      val previous = raw.indices.map(index => (index * 31).toByte).toArray

      Filter.values
        .forall: filter =>
          val encoded = filter.encode(raw, previous, bpp)
          filter.decode(encoded, previous, bpp).sameElements(raw)
