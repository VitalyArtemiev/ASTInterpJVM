var a: int
var b:int=1
var c :int=-2
var abc: int = -1- 2 + 3+4
a:= -5
a :=- 4
var f: float = 5.1
f:= -4.2345
fun f1(){}
fun myFunction(b: int): int {
    var a: int
    a := 1 * 4 - 5 * (b +1)

    return a
}
fun changeC(a: int, b: int) {
    c:= c + a * b
}
{
    c:= myFunction(3)
    writeLn(c)
    changeC(5, 6)
    writeLn(c)
    myFunction(4)
    writeLn(myFunction(5))
    if a + b < 7 {
        c:= c+a +3
        a :=-b-c+a+4
    }
    var i: int = 1
    while i <5 {
        f:= f + 1
        c := c*2
        b :=a +(a- b)
        i := i + 1
    }
    f := f * 3 / 2
    {{}}
        { { } }
        {  {}}
    f := pi
    writeLn(f < 3)
    _PRINTVARTABLE()
}