package parsec

import arrow.core.Tuple4

@JvmName("and2")
operator fun <C,T,S> Parsec<C,T>.times(that: Parsec<C, S>) =
    this.and(that)

@JvmName("and3")
operator fun <C,T1,T2,T3> Parsec<C, Pair<T1, T2>>.times(that: Parsec<C, T3>): Parsec<C, Triple<T1,T2,T3>> =
    this.and(that).map { (pr, t3) -> Triple(pr.first, pr.second, t3) }

@JvmName("and4")
operator fun <C,T1,T2,T3,T4> Parsec<C,Triple<T1, T2, T3>>.times(that: Parsec<C, T4>): Parsec<C, Tuple4<T1,T2,T3,T4>> =
    this.and(that).map { (tri, t4) -> Tuple4(tri.first, tri.second, tri.third, t4) }

