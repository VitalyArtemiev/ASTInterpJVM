package util

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.checkAll

class RandomAccessIteratorTest: FunSpec( {
    context("manual tests") {
        test("basic") {
            val al = ArrayList<Int>()
            al.add(1)
            al.add(2)
            al.add(3)
            al.add(4)

            val i = al.toRandomAccessIterator()
            i.next() shouldBe 1
            i.next() shouldBe 2
            i.next() shouldBe 3
            i.next() shouldBe 4
        }
    }

    context("automatic tests") {
        test("generated sequence") {
            checkAll<String> {s -> s shouldNotContain "z"}

            forAll<Int, Int, Int, Int, Int> { a, b, c, d, e ->
                val l = arrayListOf<Int>(a, b, c, d, e)
                val rai = RandomAccessIterator<Int>(l)
            }

//            val list = Arb.list(Arb.int()).samples()
//            val rai = RandomAccessIterator<Int>(list)
//
//
//
//            checkAll(Arb.list(Arb.int()))
        }
    }
})