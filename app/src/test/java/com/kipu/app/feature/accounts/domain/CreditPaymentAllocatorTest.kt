package com.kipu.app.feature.accounts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditPaymentAllocatorTest {
    @Test
    fun `payment amortizes the oldest due installment first then partially reduces the current one`() {
        val schedule = listOf(
            DuePrincipalInstallment("later", dueDateEpochDay = 20, purchaseOccurredAtMillis = 1, installmentNumber = 1, outstandingPrincipalMinor = 300),
            DuePrincipalInstallment("oldest", dueDateEpochDay = 10, purchaseOccurredAtMillis = 2, installmentNumber = 1, outstandingPrincipalMinor = 100),
            DuePrincipalInstallment("same-day-later", dueDateEpochDay = 10, purchaseOccurredAtMillis = 3, installmentNumber = 1, outstandingPrincipalMinor = 200),
        )

        val allocations = CreditPaymentAllocator.allocate(paymentMinor = 250, installments = schedule)

        assertEquals(listOf("oldest", "same-day-later"), allocations.map { it.installmentId })
        assertEquals(listOf(100L, 150L), allocations.map { it.allocatedMinor })
        assertTrue(allocations.first().fullyPaid)
        assertFalse(allocations.last().fullyPaid)
    }

    @Test
    fun `payment rejects amounts above all posted principal`() {
        val error = runCatching {
            CreditPaymentAllocator.allocate(
                paymentMinor = 101,
                installments = listOf(
                    DuePrincipalInstallment("one", 1, 1, 1, 100),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
    @Test
    fun `same due date uses purchase date then installment number then identity`() {
        val rows = listOf(DuePrincipalInstallment("z", 1, 1, 2, 110), DuePrincipalInstallment("b", 1, 1, 1, 110),
            DuePrincipalInstallment("a", 1, 1, 1, 110), DuePrincipalInstallment("new", 1, 2, 1, 110))
        val result = CreditPaymentAllocator.allocate(440, rows)
        assertEquals(listOf("a", "b", "z", "new"), result.map { it.installmentId })
        assertTrue(result.all { it.fullyPaid })
    }

    @Test
    fun `payment can partially cover a principal plus interest obligation`() {
        val result = CreditPaymentAllocator.allocate(1050, listOf(DuePrincipalInstallment("financed", 1, 1, 1, 1100)))
        assertEquals(1050L, result.single().allocatedMinor)
        assertFalse(result.single().fullyPaid)
    }

    @Test
    fun `nonpositive payments and balances are rejected`() {
        for (payment in listOf(0L, -1L)) assertTrue(runCatching {
            CreditPaymentAllocator.allocate(payment, listOf(DuePrincipalInstallment("one", 1, 1, 1, 100)))
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { CreditPaymentAllocator.allocate(1, listOf(DuePrincipalInstallment("one", 1, 1, 1, 0))) }
            .exceptionOrNull() is IllegalArgumentException)
    }

}
