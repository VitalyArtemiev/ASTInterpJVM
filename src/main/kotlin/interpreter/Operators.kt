package interpreter

import java.lang.Exception
import kotlin.math.pow

operator fun Any.plus(other: Any): Number {
    return when (this) {
        is Int -> {
            when (other) {
                is Int   -> this + other
                is Float -> this + other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        is Float -> {
            when (other) {
                is Int   -> this + other
                is Float -> this + other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        else -> throw Exception("Expected number, got ${other.javaClass}")
    }
}

operator fun Any.unaryMinus(): Number {
    return when (this) {
        is Int -> - this
        is Float -> - this
        else -> throw Exception("Expected number, got ${this.javaClass}")
    }
}

operator fun Any.minus(other: Any): Number {
    return when (this) {
        is Int -> {
            when (other) {
                is Int   -> this - other
                is Float -> this - other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        is Float -> {
            when (other) {
                is Int   -> this - other
                is Float -> this - other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        else -> throw Exception("Expected number, got ${other.javaClass}")
    }
}

operator fun Any.times(other: Any): Number {
    return when (this) {
        is Int -> {
            when (other) {
                is Int   -> this * other
                is Float -> this * other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        is Float -> {
            when (other) {
                is Int   -> this * other
                is Float -> this * other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        else -> throw Exception("Expected number, got ${other.javaClass}")
    }
}

operator fun Any.div(other: Any): Number {
    return when (this) {
        is Int -> {
            when (other) {
                is Int   -> this / other
                is Float -> this / other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        is Float -> {
            when (other) {
                is Int   -> this / other
                is Float -> this / other
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        else -> throw Exception("Expected number, got ${other.javaClass}")
    }
}

infix fun Any.pow(other: Any): Number {
    return when (this) {
        is Int -> {
            when (other) {
                is Int   -> this.toFloat().pow(other).toInt()
                is Float -> this.toFloat().pow(other)
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        is Float -> {
            when (other) {
                is Int   -> this.pow(other)
                is Float -> this.pow(other)
                else -> throw Exception("Expected number, got ${other.javaClass}")
            }
        }
        else -> throw Exception("Expected number, got ${other.javaClass}")
    }
}

operator fun Any.not(): Boolean {
    return ! (this as Boolean)
}

infix fun Any.or(other: Any): Boolean {
    return this as Boolean || other as Boolean
}

infix fun Any.and(other: Any): Boolean {
    return this as Boolean && other as Boolean
}
infix fun Any.xor(other: Any): Boolean {
    return this as Boolean xor other as Boolean
}

/*infix fun Any.equal(other: Any): Boolean {
    return this === other
}

less
greater
notEqual
lequal
gequal
*/
