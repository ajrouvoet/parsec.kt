package parsec

import arrow.core.*
import java.util.NoSuchElementException

object UnexpectedEOS: Exception("Unexpected end of stream")

/**
 * Immutable Stream abstraction
 */
fun interface Stream<out C> {
    fun next(): Option<Pair<C, Stream<C>>>

    companion object {
        fun <A> empty() = Stream<A> { none() }
    }
}

fun <C> Stream<C>.asSequence() = Sequence { StreamIterator(this) }
class StreamIterator<C>(str: Stream<C>): Iterator<C> {
    private var _cursor = str
    override fun hasNext(): Boolean = _cursor.next().isSome()
    override fun next(): C = when (val n = _cursor.next()) {
        None    -> throw NoSuchElementException()
        is Some -> {
            _cursor = n.value.second
            n.value.first
        }
    }
}

data class StringView(val seq: CharSequence, val cursor: Int = 0): Stream<Char> {
    override fun next(): Option<Pair<Char, Stream<Char>>> =
            try {
                val n = seq[cursor]
                Pair(n, StringView(seq, cursor + 1)).some()
            }
            catch (e: IndexOutOfBoundsException) { none() }

    override fun toString(): String = seq.drop(cursor).toString()
}

val String.stream get() = StringView(this)