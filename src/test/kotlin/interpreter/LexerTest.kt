package interpreter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe


class LexerTest: FunSpec({
    val l = Lexer("")
    context("manual tests") {
        test("should be no lexical errors") {
            val tokens = l.lex("langtest.tl")

            l.errors.size shouldBe 0
        }
        test("should be 1 lexical error") {
            val tokens = l.lex("source.tl")

            l.errors.size shouldBe 1
        }
    }

    context("Automatic tests") {
        test("") {
            TODO()
        }
        xtest("") {

        }
    }
})