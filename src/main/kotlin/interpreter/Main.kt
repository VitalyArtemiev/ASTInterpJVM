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

    var path = "sourceExample"

    val l = Lexer(terminalsText)

    var tokens = l.lex(path)

    val p = Parser(/*nonTerminalsText*/)

    var tree = p.parse(tokens)

    //val r = Runner()

    //r.run(tree)
}
