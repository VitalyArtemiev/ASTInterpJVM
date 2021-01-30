package interpreter

import kotlin.random.Random
import kotlin.reflect.full.createInstance

class FullSystemTest {
    // @Test
    fun test() {
        val prog = Prog(nextToken())
        val n = Stmt::class.sealedSubclasses.count()

        for (i in 0..10) {
            val c = Stmt::class.sealedSubclasses[Random.nextInt(n)]
            val pars = c.constructors.first().parameters

            for (p in pars) {
                //p.type.
            }
            val params = arrayOf( c.createInstance())

            val o = c.constructors.first().call(params)
            prog.nodes.add(o)
        }
    }

    fun nextToken(): Token {
        return Token(i, i.toString(), TokenTypeEnum.TBD, 0)
    }

    var i: Int = 0
}