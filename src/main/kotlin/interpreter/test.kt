package interpreter

class aaa(){}

fun main() {
    var a: aaa?
    if ((0..1).random() == 1) {
        a = null
    } else {
        a = aaa()
    }
    var l = a ?: -1
    println(l)
    println("${l::class.simpleName}")
    if ((0..1).random() == 1) {
        a = null
    } else {
        a = aaa()
    }
    l = a ?: -1
    println(l)
    println("${l::class.simpleName}")

    var b = if((0..1).random() == 1) {
        aaa()
    } else {
        false
    }
}