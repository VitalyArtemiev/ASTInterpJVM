{
var a: int
var b: float
var c: float
    a:=-1
    b:= 2 + 3.1412
    c:= a - b

    if a + b < 7 {
        b:= b + 1
    }

    while c < b {
        c:= c + 1
        a:= a / 2
    }

    b:= a * c

    _PRINTVARTABLE()
}
