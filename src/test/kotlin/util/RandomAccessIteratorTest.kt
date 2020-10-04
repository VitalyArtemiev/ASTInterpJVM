package util

//import kotlin.test.Test
import kotlin.test.assertEquals

class RandomAccessIteratorTest {
  //  @Test
    fun testRAI() {
        val al = ArrayList<Int>()
        al.add(1)
        al.add(2)
        al.add(3)
        al.add(4)

        val i = al.toRandomAccessIterator()
        assertEquals(1, i.next())
        assertEquals(2, i.next())
        assertEquals(3, i.next())
        assertEquals(4, i.next())
    }
}