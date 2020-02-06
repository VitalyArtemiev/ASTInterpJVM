package interpreter

import interpreter.AST.*

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

val defaultIdentifiers: Array<ExportIdentifier> = arrayOf(
    ExportConstant("pi", ValType.float, 3.1415),
    ExportFunction("writeLn",  arrayOf(Pair("arg", ValType.any)), ValType.none,
        PrecompiledBlock(::writeLn, Token(-2, "Precompiled function writeln", TokenTypeEnum.identifier)))
)
