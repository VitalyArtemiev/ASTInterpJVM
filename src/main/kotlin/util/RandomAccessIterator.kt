package util

class RandomAccessIterator<T> internal constructor( delegate: MutableList<T>) : MutableList<T> by delegate {
    private var cur = 0
    fun cur(): T = this[cur-1]
    fun next(): T = this[cur++]

    fun peek(): T = this[cur]

    override fun toString(): String = cur().toString()
}

fun <T> ArrayList<T>.toRandomAccessIterator(): RandomAccessIterator<T> = RandomAccessIterator(this)
