package util

import java.time.LocalTime

const val RESET = "\u001B[0m"
const val BLACK = "\u001B[30m"
const val RED = "\u001B[31m"
const val GREEN = "\u001B[32m"
const val YELLOW = "\u001B[33m"
const val BLUE = "\u001B[34m"
const val PURPLE = "\u001B[35m"
const val CYAN = "\u001B[36m"
const val WHITE = "\u001B[37m"

class Logger (val name: String = "", var showTime: Boolean = true) {
    fun i(message: String) {
        val s = construct(message)
        println("$BLUE$[INFO]@$s$RESET")
    }

    fun e(message: String) {
        val s = construct(message)
        println("$RED[ERROR]@$s$RESET")
    }

    fun w(message: String) {
        val s = construct(message)
        println("$YELLOW[WARN]@$s$RESET")
    }

    fun d(message: String) {
        val s = construct(message)
        println("$PURPLE[DEBUG]@$s$RESET")
    }

    fun l(message: String) {
        val s = construct(message)
        println(s)
    }

    fun construct(message: String): String {
        var result = ""
        if (showTime) {
            result +=  LocalTime.now().toString() + " - "
        }

        result += "$name - "

        result += message
        return result
    }

    fun p(message: String){
        println(message)
    }
}