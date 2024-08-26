package ajrouvoet.parsec

import arrow.core.*

/**
 * What is a Parser of Cs for T?
 *
 * A parser of Cs for T is a thing that takes a stream of such Cs,
 * It will consume some prefix of that stream and then either
 * give you a T value, or an error value, as well as the remainder of the stream.
 */
fun interface Parsec<C, out T> {
    fun run(s: Stream<C>): Result<C, T>
}

operator fun <C,T> Parsec<C, T>.invoke(s: Stream<C>) = this.run(s)

// We are going to define many parsers, but we are going to define them
// in layers of abstraction.
//
// In the beginning you will see some primitives that directly act on streams.
// But as we move up the abstraction levels, this will quickly disappear
// and we will only interact with the api of lower abstraction levels.

/**
 * A parser that immediately succeeds with parse result [v]
 * without consuming any input.
 */
fun <C, T> pure(v: T) = Parsec<C, T> { s ->
    Result.Ok(s, false, v)
}

/**
 * A parser that always fails with [msg] as error message,
 * without consuming any input.
 */
fun <C, T> fail(msg: String) = Parsec<C, T> { s ->
    Result.Err(s, false, msg)
}

/**
 * This is a utility to enable recursive parser definitions.
 * Without smashing the stack immediately.
 */
fun <C, T> delay(factory: () -> Parsec<C, T>) = Parsec { s ->
    factory().run(s)
}

/**
 * Specialization of [match] that matches the end of the stream.
 */
fun <C> eos(): Parsec<C, Unit> = Parsec { s ->
    if (s.next().isNone()) Result.Ok(s, false, Unit)
    else Result.Err(s, false, "Expected end of stream, still have: $s")
}

fun <C> any() = Parsec<C, C> {
    when (val next = it.next()) {
        None    -> Result.Err(it, false, "Expected input, got none.")
        is Some -> Result.Ok(next.value.second, true, next.value.first)
    }
}

/**
 * Functorial action
 */
fun <C, T, S> Parsec<C, T>.map(f: (T) -> S) = Parsec { s ->
    this@map
        .run(s)
        .map { f(value) }
}

fun <C, T> Parsec<C, T>.mapError(onErr: (String) -> String): Parsec<C, T> = Parsec { s ->
    when (val res = this@mapError.run(s)) {
        is Result.Err -> res.copy(message = onErr(res.message))
        is Result.Ok -> res
    }
}

fun <C, S, T> Parsec<C, T>.collect(pred: (T) -> Either<String, S>): Parsec<C, S> = Parsec { s ->
    this@collect
        .run(s)
        .flatMap {
            when (val x = pred(value)) {
                is Either.Right -> Result.Ok(remainder, consumed, x.value)
                is Either.Left -> Result.Err(remainder, consumed, x.value)
            }
        }
}

fun <C, T> Parsec<C, T>.filter(onErr: (T) -> String, pred: (T) -> Boolean): Parsec<C, T> =
    collect { it: T ->
        if (pred(it)) it.right()
        else onErr(it).left()
    }

/**
 * Monadic action: value-dependent sequencing of parsers.
 */
fun <C, T, S> Parsec<C, T>.flatMap(k: (T) -> Parsec<C, S>): Parsec<C, S> = Parsec { s ->
    when (val res = this@flatMap.run(s)) {
        is Result.Err -> res
        is Result.Ok -> k(res.value)
                .run(res.remainder)
                .modifyConsumed { it || res.consumed }
    }
}

/**
 * Parse [p] but rewind the consumed input when [p] fails.
 */
fun <C,T> tryOrRewind(p: Parsec<C, T>) = Parsec { s ->
    when (val res = p.run(s)) {
        is Result.Ok -> res
        is Result.Err -> Result.Err(s, false, res.message) /* rewind on failure */
    }
}

/**
 * Parse [this], or [that] if [this] failed without consuming input.
 *
 * We do not backtrack by default on [this] because that leads to bad error messages.
 * In practice, it is almost always more desirable to control the lookahead in the
 * branches manually to ensure that we do not backtrack after parsing fails at some
 * arbitrary depth of the left branch.
 */
infix fun <C,T> Parsec<C, T>.or(that: Parsec<C, T>): Parsec<C, T> = Parsec { s ->
    when (val res = this.run(s)) {
        is Result.Err -> if (res.consumed) res else that.run(s)
        is Result.Ok -> res
    }
}

/**
 * Parse [p] but don't consume any input.
 * If [p] errors, the error is propagated and input is consumed.
 */
fun <C,T> lookahead(p: Parsec<C, T>) = Parsec { s ->
    p
        .run(s)
        .flatMap { Result.Ok(s, false, value) /* rewind on success */ }
}

// Useful combinators that are defined compositionally
// ---------------------------------------------------

/**
 * A parser that consumes a single input element and succeeds if
 * that element satisfies the predicate [pred].
 * If it doesn't, then an error is produced by calling [onErr] on the consumed element.
 */
fun <C> match(pred: (C) -> Boolean, onErr: (C) -> String = {"Unexpected input."}): Parsec<C, C> =
    any<C>().filter(onErr, pred)

/**
 * A parser that consumes a single input element and succeeds if
 * that element satisfies the predicate [pred].
 * If it doesn't, then an error is produced by calling [onErr] on the consumed element.
 */
fun <C, T> match(pred: (C) -> Either<String,T>): Parsec<C, T> =
    any<C>().collect(pred)

/**
 * Parser that sequences [this] and [that].
 * If either fails, the produced parser fails.
 * If both succeed, then both their results are given.
 */
infix fun <C,S,T> Parsec<C, S>.and(that: Parsec<C, T>): Parsec<C, Pair<S, T>> = Parsec { str ->
    when (val res = this.run(str)) {
        is Result.Err -> res
        is Result.Ok ->
            that.run(res.remainder).map {
                Pair(res.value, this.value)
            }

    }
}

/**
 * Parse [this] as many times as possible, until it fails.
 * The input consumed in the failing attempt will be rewinded.
 */
fun <C,T> Parsec<C, T>.many(): Parsec<C, List<T>> = Parsec {
    // This can be implemented compositionally as:
    //
    // this.optional.flatMap {
    //     when (it) {
    //         is None -> pure(prepend)
    //         is Some -> many(prepend + it.value)
    //     }
    // }
    //
    // but the recursion depth of that implementation depends on the input.
    // so we unwind the recursion here into a loop.
    var remaining = it
    var consumed = false
    val results = mutableListOf<T>()

    while (true) {
        when (val res = this@many.run(remaining)) {
            is Result.Err -> break
            is Result.Ok -> {
                consumed  = consumed || res.consumed
                remaining = res.remainder
                results.add(res.value)
            }
        }
    }

    Result.Ok(remaining, consumed, results)
}

/**
 * Like [many], but parses [this] separated by [sep].
 */
fun <C, S, T> Parsec<C, S>.separatedBy(sep: Parsec<C, T>): Parsec<C, List<S>> =
    this.optional.flatMap {
        when (val hd = it) {
            is None -> pure(listOf())
            is Some -> (sep skipAnd this).many().map { hd.value.prependTo(it) }
        }
    }

/**
 * Parse [this] until [stop] succeeds.
 * If [this] fails, then the failure is propagated.
 */
fun <C,S,T> Parsec<C, T>.until(end: Parsec<C, S>) = Parsec<C, Pair<List<T>, S>> {
    // See [many] implementation comment for an explanation why this is a primitive.
    var remaining = it
    var consumed = false
    val results = mutableListOf<T>()

    while (true) {
        when (val res = end.run(remaining)) {
            is Result.Err -> {} /* ignore */
            is Result.Ok -> {
                consumed  = consumed || res.consumed
                remaining = res.remainder

                return@Parsec Result.Ok(remaining, consumed, Pair(results, res.value))
            }
        }

        when (val res = this@until.run(remaining)) {
            is Result.Err -> return@Parsec res
            is Result.Ok -> {
                consumed  = consumed || res.consumed
                remaining = res.remainder
                results.add(res.value)
            }
        }
    }

    @Suppress("UNREACHABLE_CODE")
    throw Error("Impossible exit")
}

fun <C,T> Parsec<C, T>.repeat(n: Int, prepend: List<T> = listOf()): Parsec<C, List<T>> =
    if (n == 0) pure(prepend)
    else this.flatMap { t -> this.repeat(n - 1, prepend + t) }

fun <C> exactly(tk: C): Parsec<C, C> = match { if (it == tk) tk.right() else "Expected $tk, got $it".left() }

fun <C> exactly(tks: List<C>): Parsec<C, List<C>> = any<C>(tks.size)
    .mapError { "Expected $tks, but stream ended prematurely."}
    .filter({ "Expected $tks, but got $it" }) { it == tks }

/**
 * Parser that sequences [this] [and] [that] but only keep the result of [that].
 */
infix fun <C,S,T> Parsec<C, S>.skipAnd(that: Parsec<C, T>): Parsec<C, T> =
    (this and that)
        .map { it.second }

/**
 * Parser that sequences [this] [and] [that] but only keep the result of [this].
 */
infix fun <C,S,T> Parsec<C, S>.andSkip(that: Parsec<C, T>) =
    (this and that)
        .map { it.first }

data class NonEmptyList<A>(val first: A, val tail: List<A>): List<A> by (first.prependTo(tail))

fun <C,T> Parsec<C, T>.plus() = (this and this.many())
    .map { (hd, tl) -> NonEmptyList(hd, tl) }

/**
 * Try all of [parsers] in order. If any succeeds, the resulting parser succeeds.
 * If all fail, the resulting parser fails, but rewinded the stream.
 */
fun <C,T> choice(parsers: List<Parsec<C, T>>, onErr: String = "No match"): Parsec<C, T> =
    if (parsers.isEmpty())
        fail(onErr)
    else { tryOrRewind(parsers.first()) or choice(parsers.drop(1)) }

/**
 * Try all of [parsers] in order. If any succeeds, the resulting parser succeeds.
 * If all fail, the resulting parser fails, but rewinded the stream.
 */
fun <C,T> choice(vararg parsers: Parsec<C, T>, onErr: String = "No match"): Parsec<C, T> =
    choice(parsers.toList(), onErr)

val <C,T> Parsec<C, T>.optional get(): Parsec<C, Option<T>> =
    tryOrRewind(this.map { it.some() }) or pure(none<T>())

/**
 * Tries to read [n] input elements and succeeds iff that works out.
 */
fun <C> any(n: Int): Parsec<C, List<C>> = any<C>().repeat(n)

val digit: Parsec<Char, Char> = match({ it.isDigit() }) { "Expected 0-9 got '$it'."}
val letter: Parsec<Char, Char> = match({ it.isLetter() }) { "Expected letter got '$it'."}
