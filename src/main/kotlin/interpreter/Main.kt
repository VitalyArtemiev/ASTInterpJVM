package interpreter

import util.Logger
import java.io.File

var terminalsPath: String = " "
var nonTerminalsPath: String = " "

fun main(args: Array<String>) {
    val logger = Logger("Main")
    var pathIndices: Int

    var terminalsText: String = try {
        val termIndex = args.indexOf("-t") + 1
        terminalsPath = args[termIndex]
        File(terminalsPath).readText()
    } catch (e: IndexOutOfBoundsException) {
        logger.e("terminalsPath arg not found")
        defaultTerminals
    } catch (e: NoSuchFileException) {
        defaultTerminals
    }

    var nonTerminalsText: String = try {
        val nonTermIndex = args.indexOf("-nt") + 1
        nonTerminalsPath = args[nonTermIndex]
        File(terminalsPath).readText()
    } catch (e: IndexOutOfBoundsException) {
        logger.e("nonTerminalsPath arg not found")
        defaultNonTerminals
    } catch (e: NoSuchFileException) {
        defaultNonTerminals
    }

    var path = "source.tl"

    val l = Lexer(terminalsText)

    var tokens = l.lex(path)

    val p = Parser(/*nonTerminalsText*/)

    var env = p.parse(tokens)

    val r = Runner(env)

    r.run()

    val A = a()
    print(A.identifiers)
}

class a {
    val identifiers: ArrayList<Identifier> = ArrayList()

    init {
        val i = Identifier("a", true)
    }

    open class b()

    inner class Identifier(val name: String, decl: Boolean): b() {
        init {
            if (identifiers == null) {
                print("wat")
            }
            if (decl) {
                if (identifiers.find {
                        it.name == name
                    } == null) {
                    identifiers.add(this)
                } else {
                    throw Exception("Identifier <$name> already exists")
                }
            } else {
                if (identifiers.find { it.name == name } == null) {
                    throw Exception("Identifier <$name> not found")
                }
            }
        }
    }
}

