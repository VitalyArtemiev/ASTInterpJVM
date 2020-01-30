package interpreter

import interpreter.AST.*

open class exportIdentifier(val name: String, val type: IdentifierType, val valType: ValType, val value: Any?)
class exportFunction(name: String, type: IdentifierType, valType: ValType, value: Any, val exec: Unit):
    exportIdentifier(name, type, valType, value)

val idPi = exportIdentifier("pi", IdentifierType.Const, ValType.float, 3.1415)


val defaultIdentifiers = arrayOf(
    exportFunction("_PRINTVARTABLE", IdentifierType.Fun)
    Identifier(IdentifierType.Fun, true,  "_PRINTVARTABLE", 0, 0)
)