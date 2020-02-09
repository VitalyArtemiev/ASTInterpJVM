package interpreter

import util.Logger
import java.io.File
import kotlin.reflect.full.memberProperties

var terminalsPath: String = " "
var nonTerminalsPath: String = " "

fun main(args: Array<String>) {
    var cv = ConstVal(0.5f, ValType.float, Token(1, "", TokenTypeEnum.plusOP, 1))

    var bp = BinOp(cv, TokenTypeEnum.plusOP, cv, Token(1, "", TokenTypeEnum.plusOP, 1))
    for (p in bp::class.memberProperties) {
        if (p is Expr) {//https://riptutorial.com/kotlin/example/23977/getting-values-of-all-properties-of-a-class
            print(p)
        }
    }



    val logger = Logger("Main")

    var pathIndices: Int

    val terminalsText: String = try {
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

    var path = "langtest.tl"

    val l = Lexer(terminalsText)

    val tokens = l.lex(path)
    if (l.errors.isNotEmpty()) {
        logger.e("Lexical errors: \n" + l.errors.toString())
    } else {
        logger.i("Lexer finished with no errors")
    }

    val r = Runner()

    val runTimeIdentifiers: Array<ExternIdentifier> = arrayOf(
        ExternFunction("_PRINTVARTABLE", null, ValType.none, PrecompiledBlock(r::printVarTable,
            Token(-2, "Precompiled function _PRINTVARTABLE", TokenTypeEnum.identifier, 1)))
    )

    val p = Parser(/*nonTerminalsText*/)
    p.import.addAll(runTimeIdentifiers)

    var env = p.parse(tokens)

    r.run(env)
}

