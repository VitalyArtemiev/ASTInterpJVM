package interpreter

import interpreter.AST.*

open class exportIdentifier(val name: String, val type: IdentifierType, val valType: ValType, val value: Any?)
class exportFunction(name: String, type: IdentifierType, valType: ValType, value: Any, val exec: Unit):
    exportIdentifier(name, type, valType, value)

val idPi = exportIdentifier("pi", IdentifierType.Const, ValType.float, 3.1415)

typealias Params = Array<Any?>

val writeLn: (params: Params?) -> Unit = {
    if (it != null) {
        for (p in it) {
            print(p)
            /*when (p) {

            }*/
        }
    }
    println()
}

val defaultIdentifiers = arrayOf(
    exportFunction("_PRINTVARTABLE", IdentifierType.Fun)
    Identifier(IdentifierType.Fun, true,  "_PRINTVARTABLE", 0, 0)
)
