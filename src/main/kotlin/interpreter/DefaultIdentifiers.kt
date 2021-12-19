package interpreter

sealed class ExternIdentifier(val name: String, val type: IdentifierType, val valType: ValType)
class ExternFunction(name: String, val params: Signature?, retType: ValType, val body: BaseBlock):
    ExternIdentifier(name, IdentifierType.Fun, retType)
class ExternConstant(name: String, valType: ValType, val value: Any):
    ExternIdentifier(name, IdentifierType.Const, valType)
class ExternVariable(name: String, valType: ValType, val defaultValue: Any):
    ExternIdentifier(name, IdentifierType.Var, valType)

typealias Signature = Array<Pair<String, ValType>>
typealias Params = Array<Any?>

fun writeLn(params: Params?) {
    if (params != null) {
        for (p in params) {
            print(p)
        }
    }
    println()
}

fun readInt(params: Params?): Int {
    return readLine()!!.toInt()
}

val defaultIdentifiers: Array<ExternIdentifier> = arrayOf(
    ExternConstant("pi", ValType.float, 3.1415f),

    ExternFunction("writeLn",  arrayOf(Pair("arg", ValType.any)), ValType.none,
        PrecompiledBlock(::writeLn,
            Token(-2, "Precompiled function writeln", TokenTypeEnum.identifier, 1))),

    ExternFunction(
        "readInt", arrayOf(), ValType.int,
        PrecompiledBlock(
            ::readInt,
            Token(-2, "Precompiled function readInt", TokenTypeEnum.identifier, 1)
        )
    )
)
