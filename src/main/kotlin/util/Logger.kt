package util

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class Logger (val name: String = "", var showTime: Boolean = true) {
    fun i(message: String) {
        val s = construct(message)
        println("[INFO]@$s")
    }

    fun e(message: String) {
        val s = construct(message)
        println("[ERROR]@$s")
    }

    fun d(message: String) {
        val s = construct(message)
        println("[DEBUG]@$s")
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