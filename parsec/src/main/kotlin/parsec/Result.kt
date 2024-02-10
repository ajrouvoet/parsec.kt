package parsec

/**
 * A parse result is either [Ok] or an [Err], but nothing else (because sealed).
 *
 * (This is a specialization of the well known algebraic data-type [arrow.core.Either],
 * which in turn is a generalization of the well known algebraic data-type [arrow.core.Option].)
 */
sealed interface Result<C, out T> {

    data class Ok<C, T>
        ( val remainder: Stream<C>
        , val value: T
        ): Result<C, T>

    data class Err<C>
        ( val remainder: Stream<C>
        , val message: String
        ): Result<C, Nothing>

}

fun <C, T, S> Result<C, T>.map(f: (T) -> S) = when (this) {
    is Result.Err -> this
    is Result.Ok  -> Result.Ok(this.remainder, f(this.value))
}

fun <C, T, S> Result<C, T>.flatMap(f: (T, Stream<C>) -> Result<C, S>) = when (this) {
    is Result.Err -> this
    is Result.Ok  -> f(this.value, this.remainder)
}