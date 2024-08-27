package ajrouvoet.parsec

import arrow.core.Tuple4
import arrow.core.Tuple5
import kotlin.jvm.JvmName

@JvmName("and2")
operator fun <C,T,S> Parsec<C, T>.times(that: Parsec<C, S>) =
    this.and(that)

@JvmName("and3")
operator fun <C,T1,T2,T3> Parsec<C, Pair<T1, T2>>.times(that: Parsec<C, T3>): Parsec<C, Triple<T1, T2, T3>> =
    this.and(that).map { (pr, t3) -> Triple(pr.first, pr.second, t3) }

@JvmName("and4")
operator fun <C,T1,T2,T3,T4> Parsec<C, Triple<T1, T2, T3>>.times(that: Parsec<C, T4>): Parsec<C, Tuple4<T1, T2, T3, T4>> =
    this.and(that).map { (tri, t4) -> Tuple4(tri.first, tri.second, tri.third, t4) }

@JvmName("and5")
operator fun <C,T1,T2,T3,T4,T5> Parsec<C, Tuple4<T1, T2, T3, T4>>.times(that: Parsec<C, T5>): Parsec<C, Tuple5<T1, T2, T3, T4, T5>> =
    this.and(that).map { (quad, t5) -> Tuple5(quad.first, quad.second, quad.third, quad.fourth, t5) }

