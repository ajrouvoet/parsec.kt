package parsec

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
    Result.Ok(s, v)
}

/**
 * A parser that always fails with [msg] as error message,
 * without consuming any input.
 */
fun <C, T> fail(msg: String) = Parsec<C, T> { s ->
    Result.Err(s, msg)
}

/**
 * This is a utility to enable recursive parser definitions.
 * Without smashing the stack immediately.
 */
fun <C, T> rec(factory: () -> Parsec<C, T>) = Parsec { s ->
    factory().run(s)
}

/**
 * Specialization of [match] that matches the end of the stream.
 */
fun <C> eos(): Parsec<C, Unit> = Parsec { s ->
    if (s.next().isNone()) Result.Ok(s, Unit)
    else Result.Err(s, "Expected end of stream, still have: $s")
}

fun <C> any() = Parsec<C, C> {
    when (val next = it.next()) {
        None    -> Result.Err(it, "Expected input, got none.")
        is Some -> Result.Ok(next.value.second, next.value.first)
    }
}

/**
 * Functorial action
 */
fun <C, T, S> Parsec<C,T>.map(f: (T) -> S) = Parsec { s ->
    when (val res = this@map.run(s)) {
        is Result.Err -> res
        is Result.Ok  -> Result.Ok(res.remainder, f(res.value))
    }
}

fun <C, T> Parsec<C, T>.mapError(onErr: (String) -> String): Parsec<C, T> = Parsec { s ->
    when (val res = this@mapError.run(s)) {
        is Result.Err -> res.copy(message = onErr(res.message))
        is Result.Ok  -> res
    }
}

fun <C, S, T> Parsec<C,T>.collect(pred: (T) -> Either<String, S>): Parsec<C, S> = Parsec { s ->
    when (val res = this@collect.run(s)) {
        is Result.Err -> res
        is Result.Ok  ->
            when (val x = pred(res.value)) {
                is Either.Right -> Result.Ok(res.remainder, x.value)
                is Either.Left  -> Result.Err(res.remainder, x.value)
            }
    }
}

fun <C, T> Parsec<C,T>.filter(onErr: (T) -> String, pred: (T) -> Boolean): Parsec<C, T> =
    collect { it: T ->
        if (pred(it)) it.right()
        else onErr(it).left()
    }

/**
 * Monadic action: value-dependent sequencing of parsers.
 */
fun <C, T, S> Parsec<C,T>.flatMap(k: (T) -> Parsec<C, S>): Parsec<C, S> = Parsec { s ->
    when (val res = this@flatMap.run(s)) {
        is Result.Err -> res
        is Result.Ok  -> k(res.value).run(res.remainder)
    }
}

/**
 * Parse [p] but rewind the consumed input when [p] fails.
 */
fun <C,T> tryOrRewind(p: Parsec<C, T>) = Parsec { s ->
    when (val res = p.run(s)) {
        is Result.Ok -> res
        is Result.Err -> Result.Err(s, res.message) /* rewind on failure */
    }
}

/**
 * Parser that sequences [this] or [that] instead if [this] fails.
 */
infix fun <C,T> Parsec<C,T>.or(that: Parsec<C, T>) = Parsec { s ->
    when (val res = tryOrRewind(this).run(s)) {
        is Result.Err -> that.run(s)
        is Result.Ok  -> res
    }
}

/**
 * Parse [p] but don't consume any input.
 * If [p] errors, the error is propagated and input is consumed.
 */
fun <C,T> lookahead(p: Parsec<C, T>) = Parsec { s ->
    when (val res = p.run(s)) {
        is Result.Ok -> Result.Ok(s, res.value) /* rewind on success */
        is Result.Err -> res
    }
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
infix fun <C,S,T> Parsec<C,S>.and(that: Parsec<C, T>): Parsec<C, Pair<S,T>> = Parsec { str ->
    this.run(str).flatMap { s, rem ->
        that.run(rem).map { t ->
            Pair(s, t)
        }
    }
}

/**
 * Parse [this] as many times as possible, until it fails.
 * The input consumed in the failing attempt will be rewinded.
 */
fun <C,T> Parsec<C,T>.many(prepend: List<T> = listOf()): Parsec<C, List<T>> = this.optional.flatMap {
    when (val res = it) {
        is None -> pure(prepend)
        is Some -> many(prepend + res.value)
    }
}

/**
 * Like [many], but parses [this] separated by [sep].
 */
fun <C, S, T> Parsec<C, S>.separatedBy(sep: Parsec<C, T>): Parsec<C, List<S>> = (
        // cons
        ( this
        * sep
        * rec { this.separatedBy(sep) }
        ) .map { (hd, _, tl) -> hd.prependTo(tl) }
    ) or (
        // singleton
        this .map { listOf(it) }
    )

/**
 * Parse [this] until [stop] succeeds.
 * If [this] fails, then the failure is propagated.
 */
fun <C,S, T> Parsec<C,T>.until(end: Parsec<C, S>, prepend: List<T> = listOf()): Parsec<C, Pair<List<T>, S>> =
    // first try to end it
    end.map { Pair(prepend, it) }
        // if that fails, we recover by parsing [this] once more and going again
        // TODO this is a deeply recursive function
        .or (this.flatMap { res -> until(end, prepend + res)})

fun <C,T> Parsec<C,T>.repeat(n: Int, prepend: List<T> = listOf()): Parsec<C, List<T>> =
    if (n == 0) pure(prepend)
    else this.flatMap { t -> this.repeat(n - 1, prepend + t) }

fun <C> exactly(tk: C): Parsec<C, C> = match { if (it == tk) tk.right() else "Expected $tk, got $it".left() }

fun <C> exactly(tks: List<C>): Parsec<C, List<C>> = any<C>(tks.size)
    .mapError { "Expected $tks, but stream ended prematurely."}
    .filter({ "Expected $tks, but got $it" }) { it == tks }

/**
 * Parser that sequences [this] [and] [that] but only keep the result of [that].
 */
infix fun <C,S,T> Parsec<C,S>.skipAnd(that: Parsec<C, T>): Parsec<C, T> =
    (this and that)
        .map { it.second }

/**
 * Parser that sequences [this] [and] [that] but only keep the result of [this].
 */
infix fun <C,S,T> Parsec<C,S>.andSkip(that: Parsec<C, T>) =
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
    else { parsers.first() or choice(parsers.drop(1)) }

/**
 * Try all of [parsers] in order. If any succeeds, the resulting parser succeeds.
 * If all fail, the resulting parser fails, but rewinded the stream.
 */
fun <C,T> choice(vararg parsers: Parsec<C, T>, onErr: String = "No match"): Parsec<C, T> =
    choice(parsers.toList(), onErr)

val <C,T> Parsec<C, T>.optional get() =
    this.map { t -> t.some() } or pure(none())

/**
 * Tries to read [n] input elements and succeeds iff that works out.
 */
fun <C> any(n: Int): Parsec<C, List<C>> = any<C>().repeat(n)

val digit: Parsec<Char, Char> = match({ it.isDigit() }) { "Expected 0-9 got '$it'."}
val letter: Parsec<Char, Char> = match({ it.isLetter() }) { "Expected letter got '$it'."}
