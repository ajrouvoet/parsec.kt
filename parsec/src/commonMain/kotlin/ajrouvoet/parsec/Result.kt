package ajrouvoet.parsec

/**
 * A parse result is either [Ok] or an [Err], but nothing else (because sealed).
 *
 * (This is a specialization of the well known algebraic data-type [arrow.core.Either],
 * which in turn is a generalization of the well known algebraic data-type [arrow.core.Option].)
 */
sealed interface Result<C, out T> {

    fun modifyConsumed(f: (Boolean) -> Boolean) = when (this) {
        is Err -> copy(consumed = f(consumed))
        is Ok -> copy(consumed = f(consumed))
    }

    data class Ok<C, T>
        (/** the remainder of the stream */
          val remainder: Stream<C>,
         /** whether any input was consumed */
          val consumed: Boolean,
         /** the result of parsing */
          val value: T
        ): Result<C, T>

    data class Err<C>
        (val remainder: Stream<C>,
         /** whether any input was consumed */
          val consumed: Boolean,
         /** the error message */
          val message: String
        ): Result<C, Nothing>

}

fun <C, T, S> Result<C, T>.map(f: Result.Ok<C, T>.() -> S) = when (this) {
    is Result.Err -> this
    is Result.Ok -> Result.Ok(remainder, consumed, f())
}

fun <C, T, S> Result<C, T>.flatMap(f: Result.Ok<C, T>.() -> Result<C, S>) = when (this) {
    is Result.Err -> this
    is Result.Ok -> f()
}