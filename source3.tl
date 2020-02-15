var abc: int
abc := 10 + 3 * 4
var d: int = abc
fun f(): int {
    var a: int
    a := 5 + 10
    a := 2 * a
    d:= 0
    return a
}

var e: int = f()
var e1: int = e