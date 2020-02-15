fun f(b: int): int {
    return 500 + b
}

async a: int = f(6)

var b: float = 1.0

await a
writeLn(a)