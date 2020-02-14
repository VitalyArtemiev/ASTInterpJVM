# this is a comment
_PRINTVARTABLE()
assertEquals(0, 0)
assert(0 = 0)
assertEquals(0 = 0, true)
assertEquals(0 = 1, false)
assert(not (0 <> 0))
assert(0 <> 1)
assert(1 > 0)
assert(-1 < 0)

assert(0 = 0.0)
assertEquals(0 + 1, 1)
assertEquals(1 + 1, 2)
assertEquals(0 - 1, -1)
assertEquals(2 - 1, 1)

assertEquals(30 - 40, -10)
assertEquals(0.0 + 1, 1.0)
assertEquals(1 + 1.0, 2.0)
assertEquals(0.0 - 1.0, -1.0)
assertEquals(2 - 1.0, 1.0)
assertEquals(30.0 - 40, -10.0)

var a: int = 50
assertEquals(a, 50)

var b: int
b:= -30
assertEquals(b, -30)

assertEquals(a + b, 20)

a:= 10
b:= 5
assertEquals(a / b, 2)

a:= 1
b:= 3
assertEquals(a / b, 0)

var c: float = 1.0
var d: float = 3.0
assertEquals(c / d, 0.33333334)

assertEquals(3 * 10 / 3 , 10)

assertEquals(5 > 6, false)
assertEquals(5 >= 5, true)
assertEquals(5 <= 6, true)

assertEquals(5 * 2 + 3, 13)
assertEquals(5 * (2 + 3), 25)
_PRINTVARTABLE()

a := 0
b := 5

while (a <= 5) {
    a:= a + 1
    b:= b - 1
}
assertEquals(a , 6)
assertEquals(b, -1)

if true {
    a:= 1
}
assertEquals(a, 1)

if false {
    a:= 0
}
assertEquals(a, 1)

fun f(): int {
    a:= 5
    return a + 1
}

b:= f()
assertEquals(a, 5)
assertEquals(b, 6)

{}

_PRINTVARTABLE()
