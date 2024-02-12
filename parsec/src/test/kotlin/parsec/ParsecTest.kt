package parsec.parsec

import parsec.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ParsecTest {

    private fun <T> Parsec<Char, T>.expectError(input: String, onErr: Result.Err<Char>.() -> Unit = {}) =
        when (val res = this.run(input.stream)) {
            is Result.Err -> res.onErr()
            is Result.Ok -> fail("Expected failed parse, but succeeded with value '${res.value}'")
        }

    private fun <T> Parsec<Char, T>.expectParse(input: String, onValue: Result.Ok<Char, T>.() -> Unit = {}) =
        when (val res = this.run(input.stream)) {
            is Result.Err -> fail("Expected successful parse, but failed with msg: ${res.message}")
            is Result.Ok -> res.onValue()
        }

    @Test
    fun `pure`() {
        val parser = pure<Char, _>(1)

        parser.expectParse("xy") {
            assertEquals(1, value)
        }
        parser.expectParse("xyz") {
            assertEquals(1, value)
        }
    }

    @Test
    fun `match`() {
        val parser = match<Char>({ it == '!' }) { "Unexpected: not an exclamation" }

        parser.expectParse("!")
        parser.expectParse("!.")
        parser.expectError(".!")
    }

    @Test
    fun `read 2x any`() {
        val parser = any<Char>(2)

        parser.expectError("x") // too little input

        parser.expectParse("xy") { assertEquals(listOf('x', 'y'), value) }
        parser.expectParse("xyz") { assertEquals(listOf('x', 'y'), value) }
    }

    @Test
    fun `exactly and exactly `() {
        val parser = exactly('x') and exactly('y')

        parser.expectError("x") // too little input

        parser.expectParse("xy")
        parser.expectParse("xyz")
    }

    @Test
    fun `exactly andSkip eos `() {
        val parser = exactly('x') andSkip eos()

        parser.expectParse("x") { assertEquals('x', value) }

        parser.expectError("") // too little input
        parser.expectError("xy") // too much input
    }

    @Test
    fun `tryOrRewind exactly`() {
        val parser = tryOrRewind(exactly('x'))

        parser.expectParse("x") {
            assertEquals('x', value)
            assertTrue(remainder.next().isNone())
        }

        parser.expectError("y") { assertTrue(remainder.next().isSome()) }
    }

    @Test
    fun until() {
        val parser = any<Char>().until(exactly(listOf('!', '!')))

        parser.expectParse("hi there! again!!") {
            val (els, sep) = value
            assertEquals("hi there! again", els.joinToString(""))
            assertEquals(listOf(), remainder.asSequence().toList())
        }

        parser.expectError("hi there! uh fail!") {
            assertEquals(listOf(), remainder.asSequence().toList(), "should not rewind on failure")
        }
    }

    @Test
    fun choose() {
        val parser = choice(exactly('a'), exactly('b'), exactly('c'))

        parser.expectParse("a")
        parser.expectError("d") {
            println(this)
        }
    }
}