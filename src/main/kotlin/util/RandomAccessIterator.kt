package util

class RandomAccessIterator<T> internal constructor( delegate: ArrayList<T>) : MutableList<T> by delegate {
    private var cur = 0
    private var saved = 0

    fun next(): T = this[cur++]

    //fun prev(): T = this[cur--]
    fun peek(): T = this[cur]

    fun save(): Int {
        saved = cur
        return cur
    }

    fun revert(index: Int = saved) {
        cur = index
    }
}

fun <T> ArrayList<T>.toRandomAccessIterator(): RandomAccessIterator<T> = RandomAccessIterator(this)
