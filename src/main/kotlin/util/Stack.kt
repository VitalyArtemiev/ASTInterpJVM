package util

import java.util.ArrayList

class Stack<T>(private val delegate: ArrayList<T> = ArrayList()): MutableList<T> by delegate {
    inline fun push(element: T) = add(element)
    inline fun pop(): T = removeAt(lastIndex)
    inline fun peek(): T = last()
    override fun toString(): String {
        var s: String = ""
        delegate.forEach { s += it.toString() + "\n"}
        return s
    }
    fun toArrayList(): ArrayList<T> {
        return delegate
    }
}
