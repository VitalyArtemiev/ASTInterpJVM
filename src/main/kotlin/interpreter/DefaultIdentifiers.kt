package interpreter

import util.Logger

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

val logger = Logger("Asserter")

fun assert(params: Params?) {
    require(params != null && params.isNotEmpty() && params[0] is Boolean)
    if (params[0] as Boolean) {
        logger.i("Assertion passed")
    }
    else {
        logger.e("Assertion failed")
    }
}

fun assertEquals(params: Params?) {
    require(params != null && params.size == 2)
    if (params[0] == params[1]) {
        logger.i("Assertion passed: ${params[0]} = ${params[1]}")
    }
    else {
        logger.e("Assertion failed: ${params[0]} = ${params[1]}")
    }
}

fun readInt(params: Params?): Int {
    return readLine()!!.toInt()
}

val defaultIdentifiers: Array<ExternIdentifier> = arrayOf(
    ExternConstant("pi", ValType.float, 3.1415f),

    ExternFunction("writeLn",  arrayOf(Pair("arg", ValType.any)), ValType.none,
        PrecompiledBlock(::writeLn,
            Token(-2, "Precompiled function writeln", TokenTypeEnum.identifier, 1))),

    ExternFunction("readInt",  arrayOf(), ValType.int,
        PrecompiledBlock(::readInt,
            Token(-2, "Precompiled function readInt", TokenTypeEnum.identifier, 1))),

    ExternFunction("assert", arrayOf(Pair("value", ValType.bool)), ValType.none,
        PrecompiledBlock(::assert,
            Token(-2, "Precompiled function assert", TokenTypeEnum.identifier, 1))),

    ExternFunction("assertEquals", arrayOf(Pair("value1", ValType.any), Pair("value2", ValType.any)),
        ValType.none,
        PrecompiledBlock(::assertEquals,
            Token(-2, "Precompiled function assertEquals", TokenTypeEnum.identifier, 1)))
)
