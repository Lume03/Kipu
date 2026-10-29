package com.kipu.app.feature.capture.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class NotificationCaptureParserTest {
    @Test fun `every provider ignores incomplete operation fixtures regardless of case or accents`() {
        val statuses = listOf("RECHAZADA", "denegada", "fallida", "anulada", "cancelada", "PENDIENTE", "revertida",
            "no procesada", "no fue procesada", "no autorizada", "declinada", "no se pudo realizar")
        for (provider in CaptureProvider.entries) {
            for (status in statuses) {
                val signal = CaptureSignal(provider, "pkg", "key", 1, provider.name,
                    "Compra $status de S/ 120.00 en PLAZA VEA con tu tarjeta débito terminada en 1234 el 28/10/2026 10:35")
                val parsed = NotificationCaptureParsers.forProvider(provider).parse(signal)
                assertNull("$provider / $status", parsed.direction)
                assertNull(parsed.amountMinor)
            }
        }
    }
    private fun parse(provider: CaptureProvider, text: String) = NotificationCaptureParsers.forProvider(provider)
        .parse(CaptureSignal(provider, "test", "key", 100, provider.name, text))

    @Test fun `provider fixtures extract direction currency amount and merchant`() {
        data class Fixture(val provider: CaptureProvider, val text: String, val amount: Long, val currency: String, val direction: CaptureDirection, val merchant: String?)
        val fixtures = listOf(
            Fixture(CaptureProvider.YAPE, "Yapeaste S/ 25.50 a Juan P.", 2550, "PEN", CaptureDirection.EXPENSE, "Juan P."),
            Fixture(CaptureProvider.YAPE, "Juan P. te yapeó S/ 10.00", 1000, "PEN", CaptureDirection.INCOME, "Juan P."),
            Fixture(CaptureProvider.YAPE, "Recibiste un yapeo de S/ 10", 1000, "PEN", CaptureDirection.INCOME, null),
            Fixture(CaptureProvider.BCP, "Consumo de S/ 120.00 en PLAZA VEA con tu tarjeta de credito terminada en 1234", 12000, "PEN", CaptureDirection.EXPENSE, "PLAZA VEA"),
            Fixture(CaptureProvider.INTERBANK, "Compra por USD 15.99 en NETFLIX.COM con tu Tarjeta Débito ****5678", 1599, "USD", CaptureDirection.EXPENSE, "NETFLIX.COM"),
            Fixture(CaptureProvider.BBVA, "Compra por US$ 1,250.50 en TIENDA con tu tarjeta terminada en 1234", 125050, "USD", CaptureDirection.EXPENSE, "TIENDA"),
            Fixture(CaptureProvider.SCOTIABANK, "Consumo de PEN 1.250,50 en TIENDA con tu tarjeta terminada en 1234", 125050, "PEN", CaptureDirection.EXPENSE, "TIENDA"),
            Fixture(CaptureProvider.PLIN, "Enviaste S/ 50.00 con Plin a Maria", 5000, "PEN", CaptureDirection.EXPENSE, "Maria"),
        )
        fixtures.forEach { fixture ->
            val result = parse(fixture.provider, fixture.text)
            assertEquals(fixture.provider.name, fixture.amount, result.amountMinor)
            assertEquals(fixture.currency, result.currency)
            assertEquals(fixture.direction, result.direction)
            assertEquals(fixture.merchant, result.merchantSourceText)
        }
    }

    @Test fun `reliable date and card evidence permit later high confidence scoring`() {
        val parsed = parse(CaptureProvider.BCP, "Consumo de S/ 120.00 en PLAZA VEA con tu tarjeta de crédito terminada en 1234 el 28/10/2026 10:35 a. m.")
        assertEquals(CaptureInstrumentKind.CREDIT_CARD, parsed.instrumentKind)
        assertEquals("1234", parsed.cardLast4)
        assertTrue(parsed.occurredAtReliable)
        assertEquals(LocalDateTime.of(2026, 10, 28, 10, 35).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli(), parsed.occurredAt)
    }

    @Test fun `missing and invalid dates remain inferred`() {
        listOf("", " el 31/02/2026 10:35", " el 28/10/2026", " el 28/10/2026 25:61").forEach {
            val parsed = parse(CaptureProvider.YAPE, "Yapeaste S/ 10 a Ana$it")
            assertFalse(parsed.occurredAtReliable)
            assertNull(parsed.occurredAt)
            assertTrue(CaptureIssue.DATE_INFERRED in parsed.issues)
        }
    }

    @Test fun `title may carry transaction fields while body carries the merchant`() {
        val result = BcpNotificationParser().parse(CaptureSignal(CaptureProvider.BCP, "test", "key", 100,
            "Compra por USD 15.99", "En NETFLIX.COM con tu Tarjeta Débito ****5678"))
        assertEquals(CaptureDirection.EXPENSE, result.direction)
        assertEquals(1599L, result.amountMinor)
        assertEquals("NETFLIX.COM", result.merchantSourceText)
        assertEquals("5678", result.cardLast4)
    }

    @Test fun `low evidence retains known direction but never invents amount`() {
        val parsed = parse(CaptureProvider.YAPE, "Yapeaste a Ana")
        assertEquals(CaptureDirection.EXPENSE, parsed.direction)
        assertNull(parsed.amountMinor)
        assertTrue(CaptureIssue.AMOUNT_MISSING in parsed.issues)
        assertNull(parse(CaptureProvider.BCP, "Promoción especial").direction)
    }

    @Test fun `amounts use exact minor units and reject overflow malformed negative or competing values`() {
        mapOf("0.01" to 1L, "1,000" to 100000L, "1.000.000,99" to 100000099L, "92233720368547758.07" to Long.MAX_VALUE).forEach { (raw, expected) ->
            assertEquals(expected, parse(CaptureProvider.YAPE, "Yapeaste S/ $raw a Ana").amountMinor)
        }
        listOf("0", "-10", "12,34,56", "1.000.00", "92233720368547758.08").forEach { raw ->
            assertNull(raw, parse(CaptureProvider.YAPE, "Yapeaste S/ $raw a Ana").amountMinor)
        }
        val competing = parse(CaptureProvider.BCP, "Compra S/ 10 en TIENDA saldo S/ 100")
        assertNull(competing.amountMinor)
        assertTrue(CaptureIssue.AMOUNT_AMBIGUOUS in competing.issues)
    }
}
