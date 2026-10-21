package com.kipu.app.feature.receipts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class YapeReceiptParserTest {
    @Test fun `opening inverted exclamation OCR glyph does not reverse payment direction`() {
        assertEquals(true, YapeReceiptParser().parse("iTe Yapearon!\nS/ 20").isIncome)
        assertEquals(false, YapeReceiptParser().parse("iYapeaste!\nS/ 20").isIncome)
        assertEquals(true, YapeReceiptParser().parse("¡Te Yapearonl\nS/ 20").isIncome)
    }
    @Test fun `logo currency symbol is not the financial amount anchor`() {
        val result = YapeReceiptParser().parse("S/\nyape\nARTE CONMEMORATIVO\n¡Yapeaste!\nS/ 12.50\nPersona Demo*\n21 oct. 2026 10:35 a. m.\nNro. de operación 00012345")
        assertEquals("Persona Demo*", result.recipient)
        assertEquals(1250L, result.amountMinor)
    }

    private val parser = YapeReceiptParser()
    private val limaZone = ZoneId.of("America/Lima")

    private fun expectedEpochMilli(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Long {
        val zdt = ZonedDateTime.of(
            LocalDate.of(year, month, day),
            LocalTime.of(hour, minute, second),
            limaZone
        )
        return zdt.toInstant().toEpochMilli()
    }

    @Test
    fun `parses standard Yape receipt with dot decimal amount`() {
        val text = """
            ¡Yapeaste!
            S/ 12.50
            Yapeaste a
            Juan Perez
            Fecha y hora: 21 oct. 2026 - 10:35 a. m.
            Número de celular: *** *** 123
            Número de operación: 12345678
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(1250L, result.amountMinor)
        assertEquals("Juan Perez", result.recipient)
        assertEquals("12345678", result.reference)
        assertEquals(false, result.isIncome)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 21, 10, 35)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue("Issues should be empty for valid receipt: ${result.issues}", result.issues.isEmpty())
    }

    @Test
    fun `parses thousands amount with comma and dot decimals`() {
        val text = """
            ¡Yapeaste!
            S/1,200.50
            Destinatario: Maria Elena Rojas
            21 de octubre de 2026 - 08:15 p. m.
            Nro. de operación: 98765432
            Código de seguridad: 456
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(120050L, result.amountMinor)
        assertEquals("Maria Elena Rojas", result.recipient)
        assertEquals("98765432", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 21, 20, 15)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `parses decimal with comma format`() {
        val text = """
            ¡Yapeaste!
            S/ 12,50
            Yapeaste a Carlos Ruiz
            21 oct 2026, 11:20 am
            Código de operación: 87654321
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(1250L, result.amountMinor)
        assertEquals("Carlos Ruiz", result.recipient)
        assertEquals("87654321", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 21, 11, 20)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `parses thousands with dot and comma decimals`() {
        val text = """
            ¡Yapeaste!
            S/ 1.200,50
            Destino: Pedro Pablo
            Fecha: 15/09/2026 14:30
            Nro de operación: 11223344
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(120050L, result.amountMinor)
        assertEquals("Pedro Pablo", result.recipient)
        assertEquals("11223344", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 9, 15, 14, 30)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `ignores 3-digit security code and preserves operation reference`() {
        val text = """
            ¡Yapeaste!
            S/ 50.00
            Yapeaste a Ana Gomez
            Código de seguridad: 789
            Nro. de operación: 55667788
            21 oct. 2026 10:35 a. m.
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals("55667788", result.reference)
        assertNotEquals("789", result.reference)
        assertEquals(5000L, result.amountMinor)
        assertEquals("Ana Gomez", result.recipient)
    }

    @Test
    fun `rejects 3-digit code when only security code is present`() {
        val text = """
            ¡Yapeaste!
            S/ 50.00
            Yapeaste a Ana Gomez
            Código de seguridad: 789
            21 oct. 2026 10:35 a. m.
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.reference)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_MISSING_REFERENCE))
    }

    @Test
    fun `rejects operation code that is 3 digits`() {
        val text = """
            ¡Yapeaste!
            S/ 50.00
            Yapeaste a Ana Gomez
            Operación: 123
            21 oct. 2026 10:35 a. m.
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.reference)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_MISSING_REFERENCE))
    }

    @Test
    fun `handles split currency symbol across lines`() {
        val text = """
            ¡Yapeaste!
            S/
            85.00
            Destinatario: Luis Vargas
            21 oct. 2026 09:10 a. m.
            Nro. de operación: 33445566
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(8500L, result.amountMinor)
        assertEquals("Luis Vargas", result.recipient)
        assertEquals("33445566", result.reference)
    }

    @Test
    fun `handles repeated identical amounts without ambiguity`() {
        val text = """
            ¡Yapeaste!
            S/ 25.00
            Confirmación
            Total pagado: S/ 25.00
            Destinatario: Carmen Silva
            21 oct. 2026 10:35 a. m.
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(2500L, result.amountMinor)
        assertEquals("Carmen Silva", result.recipient)
        assertEquals("44556677", result.reference)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `rejects ambiguous contradictory amounts`() {
        val text = """
            ¡Yapeaste!
            S/ 15.00
            Monto anterior S/ 40.00
            Destinatario: Carmen Silva
            21 oct. 2026 10:35 a. m.
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.amountMinor)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_AMBIGUOUS_AMOUNT))
    }

    @Test
    fun `rejects zero amount`() {
        val text = """
            ¡Yapeaste!
            S/ 0.00
            Destinatario: Carmen Silva
            21 oct. 2026 10:35 a. m.
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.amountMinor)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_ZERO_OR_NEGATIVE_AMOUNT))
    }

    @Test
    fun `rejects negative amount`() {
        val text = """
            ¡Yapeaste!
            S/ -20.50
            Destinatario: Carmen Silva
            21 oct. 2026 10:35 a. m.
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.amountMinor)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_ZERO_OR_NEGATIVE_AMOUNT))
    }

    @Test
    fun `rejects amount overflow`() {
        val text = """
            ¡Yapeaste!
            S/ 99999999999999999999999999.00
            Destinatario: Carmen Silva
            21 oct. 2026 10:35 a. m.
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.amountMinor)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_AMOUNT_OVERFLOW))
    }

    @Test
    fun `does not invent date or time when date is missing`() {
        val text = """
            ¡Yapeaste!
            S/ 30.00
            Destinatario: Carmen Silva
            Hora: 10:35 a. m.
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertNull("OccurredAt must be null if date is missing (no fallback to current date)", result.occurredAt)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_MISSING_DATE_TIME))
    }

    @Test
    fun `does not invent date or time when time is missing`() {
        val text = """
            ¡Yapeaste!
            S/ 30.00
            Destinatario: Carmen Silva
            Fecha: 21 oct. 2026
            Nro de operación: 44556677
        """.trimIndent()

        val result = parser.parse(text)

        assertNull("OccurredAt must be null if time is missing (no fallback to 00:00 or current time)", result.occurredAt)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_MISSING_DATE_TIME))
    }

    @Test
    fun `parses 12 AM midnight and 12 PM noon correctly`() {
        val textMidnight = """
            ¡Yapeaste! S/ 10.00 Yapeaste a Luis 21 oct. 2026 - 12:05 a. m. Nro. de operación: 11112222
        """.trimIndent()
        val resultMidnight = parser.parse(textMidnight)
        assertEquals(expectedEpochMilli(2026, 10, 21, 0, 5), resultMidnight.occurredAt)

        val textNoon = """
            ¡Yapeaste! S/ 10.00 Yapeaste a Luis 21 oct. 2026 - 12:30 p. m. Nro. de operación: 11112222
        """.trimIndent()
        val resultNoon = parser.parse(textNoon)
        assertEquals(expectedEpochMilli(2026, 10, 21, 12, 30), resultNoon.occurredAt)
    }

    @Test
    fun `parses Spanish month names including septiembre variants`() {
        val textSet = """
            ¡Yapeaste! S/ 15.00 Yapeaste a Rosa 21 set. 2026 09:00 am Nro de operación: 99887766
        """.trimIndent()
        val resultSet = parser.parse(textSet)
        assertEquals(expectedEpochMilli(2026, 9, 21, 9, 0), resultSet.occurredAt)

        val textDic = """
            ¡Yapeaste! S/ 15.00 Yapeaste a Rosa 31 de diciembre de 2025 - 11:59 pm Nro de operación: 99887766
        """.trimIndent()
        val resultDic = parser.parse(textDic)
        assertEquals(expectedEpochMilli(2025, 12, 31, 23, 59), resultDic.occurredAt)
    }

    @Test
    fun `returns null and issues on blank or corrupted text`() {
        val blankResult = parser.parse("   ")
        assertNull(blankResult.amountMinor)
        assertNull(blankResult.occurredAt)
        assertNull(blankResult.recipient)
        assertNull(blankResult.reference)
        assertTrue(blankResult.issues.contains(YapeReceiptParser.ISSUE_EMPTY_TEXT))

        val gibberishResult = parser.parse("blur blur noise unreadable line ???")
        assertNull(gibberishResult.amountMinor)
        assertNull(gibberishResult.occurredAt)
        assertNull(gibberishResult.recipient)
        assertNull(gibberishResult.reference)
        assertTrue(gibberishResult.issues.contains(YapeReceiptParser.ISSUE_MISSING_AMOUNT))
        assertTrue(gibberishResult.issues.contains(YapeReceiptParser.ISSUE_MISSING_DATE_TIME))
        assertTrue(gibberishResult.issues.contains(YapeReceiptParser.ISSUE_MISSING_RECIPIENT))
        assertTrue(gibberishResult.issues.contains(YapeReceiptParser.ISSUE_MISSING_REFERENCE))
    }

    @Test
    fun `same amount but different reference are not equal`() {
        val receipt1 = YapeReceiptFields(
            amountMinor = 1250L,
            occurredAt = 1761050100000L,
            recipient = "Juan Perez",
            reference = "12345678",
            issues = emptyList()
        )

        val receipt2 = YapeReceiptFields(
            amountMinor = 1250L,
            occurredAt = 1761050100000L,
            recipient = "Juan Perez",
            reference = "87654321",
            issues = emptyList()
        )

        assertNotEquals(
            "Receipts with identical amount but different reference must NOT be equal",
            receipt1,
            receipt2
        )

        assertEquals("Receipt must equal itself or its identical copy", receipt1, receipt1.copy())
    }

    @Test
    fun `positional recipient between amount and date is extracted when no label exists`() {
        val text = """
            ¡Yapeaste!
            S/ 28.00
            Dora Alicia Mendez
            21 oct. 2026 - 10:35 a. m.
            Número de celular: *** *** 999
            Nro. de operación: 90817263
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals("Dora Alicia Mendez", result.recipient)
        assertEquals(2800L, result.amountMinor)
        assertEquals("90817263", result.reference)
        assertNotNull(result.occurredAt)
    }

    @Test
    fun `reference label on one line and value on next line is extracted`() {
        val text = """
            ¡Yapeaste!
            S/ 70.00
            Yapeaste a Jorge Castro
            Fecha y hora: 21 oct. 2026 - 10:35 a. m.
            Número de operación
            65748392
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals("65748392", result.reference)
        assertEquals("Jorge Castro", result.recipient)
        assertEquals(7000L, result.amountMinor)
    }

    @Test
    fun `parses real format income receipt with integer amount and leading zero reference`() {
        val text = """
            ¡Te Yapearon!
            S/20
            Persona Ejemplo*
            20 oct. 2026 | 12:40 a. m.
            CÓDIGO DE SEGURIDAD
            4 8 1
            DATOS DE LA TRANSACCIÓN
            Nro. de celular
            *** *** 209
            Destino
            Yape
            Nro. de operación
            00012345
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(true, result.isIncome)
        assertEquals(2000L, result.amountMinor)
        assertEquals("Persona Ejemplo*", result.recipient)
        assertEquals("00012345", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 20, 0, 40)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `parses real format expense receipt with decimal amount`() {
        val text = """
            ¡Yapeaste!
            S/46.70
            Persona Dos*
            19 oct. 2026 | 07:46 p. m.
            CÓDIGO DE SEGURIDAD
            1 5 2
            DATOS DE LA TRANSACCIÓN
            Nro. de celular
            *** *** 175
            Destino
            Yape
            Nro. de operación
            87654321
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(false, result.isIncome)
        assertEquals(4670L, result.amountMinor)
        assertEquals("Persona Dos*", result.recipient)
        assertEquals("87654321", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 19, 19, 46)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `parses income receipt with integer amount without security code and bank counterparty`() {
        val text = """
            ¡Te Yapearon!
            S/80
            BCP - Persona Tres
            19 oct. 2026 | 07:24 p. m.
            DATOS DE LA TRANSACCIÓN
            Destino Yape
            Nro. de operación 00012346
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(true, result.isIncome)
        assertEquals(8000L, result.amountMinor)
        assertEquals("BCP - Persona Tres", result.recipient)
        assertEquals("00012346", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 19, 19, 24)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `does not confuse Destino Yape or Destino Plin with recipient`() {
        val text = """
            ¡Yapeaste!
            S/5
            Persona Cuatro
            16 oct. 2026 | 12:18 p. m.
            DATOS DE LA TRANSACCIÓN
            Nro. de celular *** *** 862
            Destino
            Plin
            Nro. de operación
            3056917
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(false, result.isIncome)
        assertEquals(500L, result.amountMinor)
        assertEquals("Persona Cuatro", result.recipient)
        assertEquals("3056917", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 16, 12, 18)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `parses service payment with internet payment code and integer amount`() {
        val text = """
            ¡Yapeaste el servicio!
            S/ 100
            Empresa Servicios
            20 oct. 2026 | 12:28 a. m.
            Pago por internet: Nº
            TESTPAYMENT00000000000001
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(false, result.isIncome)
        assertEquals(10000L, result.amountMinor)
        assertEquals("Empresa Servicios", result.recipient)
        assertEquals("TESTPAYMENT00000000000001", result.reference)
        val expectedOccurredAt = expectedEpochMilli(2026, 10, 20, 0, 28)
        assertEquals(expectedOccurredAt, result.occurredAt)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `defensive handling when OCR reorders columns and reference cannot be determined`() {
        val text = """
            ¡Te Yapearon!
            S/20
            Persona Cinco*
            20 oct. 2026 | 12:40 a. m.
            DATOS DE LA TRANSACCIÓN
            Nro. de celular
            Destino
            Nro. de operación
            *** *** 209
            Yape
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(true, result.isIncome)
        assertEquals(2000L, result.amountMinor)
        assertEquals("Persona Cinco*", result.recipient)
        assertNull(result.reference)
        assertTrue(result.issues.contains(YapeReceiptParser.ISSUE_MISSING_REFERENCE))
    }

    @Test
    fun `isIncome is null for receipts with ambiguous or missing header`() {
        val text = """
            S/ 50.00
            Persona Seis
            21 oct. 2026 - 10:35 a. m.
            Nro. de operación: 11223344
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.isIncome)
        assertEquals(5000L, result.amountMinor)
        assertEquals("Persona Seis", result.recipient)
        assertEquals("11223344", result.reference)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `isIncome is null when both income and expense headers are present`() {
        val text = """
            ¡Te Yapearon!
            ¡Yapeaste!
            S/ 30.00
            Persona Siete
            21 oct. 2026 - 10:35 a. m.
            Nro. de operación: 99887766
        """.trimIndent()

        val result = parser.parse(text)

        assertNull(result.isIncome)
    }
}
