package interpreter

import interpreter.AST.*
import util.Logger

sealed class ExportIdentifier(val name: String, val type: IdentifierType, val valType: ValType)
class ExportFunction(name: String, val params: Signature?, retType: ValType, val body: BaseBlock):
    ExportIdentifier(name, IdentifierType.Fun, retType)
class ExportConstant(name: String, valType: ValType, val value: Any):
    ExportIdentifier(name, IdentifierType.Const, valType)
class ExportVariable(name: String, valType: ValType, val defaultValue: Any):
    ExportIdentifier(name, IdentifierType.Var, valType)

typealias Signature = Array<Pair<String, ValType>>
typealias Params = Array<Any?>

fun writeLn(params: Params?) {
    if (params != null) {
        for (p in params) {
            print(p)
            /*when (p) {

            }*/
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

val defaultIdentifiers: Array<ExportIdentifier> = arrayOf(
    ExportConstant("pi", ValType.float, 3.1415f),

    ExportFunction("writeLn",  arrayOf(Pair("arg", ValType.any)), ValType.none,
        PrecompiledBlock(::writeLn,
            Token(-2, "Precompiled function writeln", TokenTypeEnum.identifier, 1))),

    ExportFunction("assert", arrayOf(Pair("value", ValType.bool)), ValType.none,
        PrecompiledBlock(::assert,
            Token(-2, "Precompiled function assert", TokenTypeEnum.identifier, 1))),

    ExportFunction("assertEquals", arrayOf(Pair("value1", ValType.any), Pair("value2", ValType.any)),
        ValType.none,
        PrecompiledBlock(::assertEquals,
            Token(-2, "Precompiled function assertEquals", TokenTypeEnum.identifier, 1)))
)
