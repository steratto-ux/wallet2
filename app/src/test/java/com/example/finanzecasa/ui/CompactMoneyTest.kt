package com.example.finanzecasa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactMoneyTest {
    @Test
    fun smallAmountsAreRoundedToWholeEuros() {
        assertEquals("0 €", compactMoney(0))
        assertEquals("0 €", compactMoney(49))
        assertEquals("1 €", compactMoney(50))
        assertEquals("4 €", compactMoney(399))
        assertEquals("141 €", compactMoney(14_050))
        assertEquals("999 €", compactMoney(99_949))
    }

    @Test
    fun sign_isIgnored() {
        assertEquals("62 €", compactMoney(-6_200))
    }

    @Test
    fun thousandsUseK() {
        assertEquals("1k €", compactMoney(100_000))
        assertEquals("1,2k €", compactMoney(123_456))
        assertEquals("9,9k €", compactMoney(990_000))
        assertEquals("12k €", compactMoney(1_234_500))
    }
}
