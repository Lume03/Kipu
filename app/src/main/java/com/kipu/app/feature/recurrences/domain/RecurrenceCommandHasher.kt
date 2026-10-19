package com.kipu.app.feature.recurrences.domain

import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Contract-v1 canonical JSON and SHA-256 for recurrence command envelopes. */
object RecurrenceCommandHasher {
    private val excludedEnvelopeKeys = setOf("operation_id", "request_hash")
    private const val BIGINT_MAX = "9223372036854775807"

    fun canonicalBytes(element: JsonElement): String {
        if (element is JsonObject && "command_type" in element) validateCommandNumbers(element)
        val canonicalInput = if (element is JsonObject && "command_type" in element) {
            JsonObject(element.filterKeys { it !in excludedEnvelopeKeys })
        } else element
        return encode(canonicalInput)
    }

    fun sha256(element: JsonElement): String = sha256(canonicalBytes(element))

    fun sha256(canonicalBytes: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonicalBytes.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun encode(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedWith { left, right -> compareUtf8(left.key, right.key) }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                encodeString(key) + ":" + encode(value)
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::encode)
        JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> encodeString(element.content)
            element.booleanOrNull != null -> element.content
            else -> normalizeNumber(element.content)
        }
    }

    private fun normalizeNumber(value: String): String = try {
        val number = BigDecimal(value)
        if (number.compareTo(BigDecimal.ZERO) == 0) "0" else number.stripTrailingZeros().toPlainString()
    } catch (exception: NumberFormatException) {
        throw IllegalArgumentException("Invalid JSON number", exception)
    }

    private fun compareUtf8(first: String, second: String): Int {
        val firstBytes = first.toByteArray(StandardCharsets.UTF_8)
        val secondBytes = second.toByteArray(StandardCharsets.UTF_8)
        for (index in 0 until minOf(firstBytes.size, secondBytes.size)) {
            val difference = (firstBytes[index].toInt() and 0xff) - (secondBytes[index].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return firstBytes.size - secondBytes.size
    }

    private fun encodeString(value: String): String {
        val output = StringBuilder(value.length + 2).append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '\u0000' -> throw IllegalArgumentException("U+0000 is not allowed in command strings")
                character.isHighSurrogate() -> {
                    require(index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                        "Unpaired high surrogate is not allowed in command strings"
                    }
                    output.append(character).append(value[index + 1])
                    index++
                }
                character.isLowSurrogate() -> throw IllegalArgumentException("Unpaired low surrogate is not allowed in command strings")
                character == '"' -> output.append("\\\"")
                character == '\\' -> output.append("\\\\")
                character == '\b' -> output.append("\\b")
                character == '\t' -> output.append("\\t")
                character == '\n' -> output.append("\\n")
                character == '\u000c' -> output.append("\\f")
                character == '\r' -> output.append("\\r")
                character.code in 0x01..0x1f -> output.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                else -> output.append(character)
            }
            index++
        }
        return output.append('"').toString()
    }

    private fun validateCommandNumbers(command: JsonObject) {
        val commandType = (command["command_type"] as? JsonPrimitive)?.content
        val contractVersion = exactInteger(command["contract_version"], "contract_version")
        require(contractVersion == BigDecimal.ONE) { "contract_version must equal 1" }
        command["expected_revision"]?.let {
            exactInteger(it, "expected_revision").requireRange("expected_revision", "1", BIGINT_MAX)
        }
        val payload = command["payload"] as? JsonObject ?: return
        payload["reminder_lead_days"]?.let {
            exactInteger(it, "payload.reminder_lead_days").requireRange("reminder_lead_days", "0", "30")
        }
        payload["interval_count"]?.let {
            val count = exactInteger(it, "payload.interval_count")
            val frequency = (payload["frequency"] as? JsonPrimitive)?.content
            val unit = (payload["custom_interval_unit"] as? JsonPrimitive)?.content
            val range = when (frequency) {
                "CUSTOM" -> when (unit) {
                    "WEEK" -> "5200"
                    "MONTH" -> "1200"
                    else -> throw IllegalArgumentException("Custom recurrence unit is invalid")
                }
                "WEEKLY", "BIWEEKLY", "MONTHLY", "ANNUALLY" -> {
                    require(count == BigDecimal.ONE) { "Built-in interval_count must equal 1" }
                    "1"
                }
                else -> if (commandType == "UPDATE_RECURRENCE") BIGINT_MAX else throw IllegalArgumentException("Recurrence frequency is invalid")
            }
            count.requireRange("interval_count", "1", range)
        }
        val template = payload["template_transaction"] as? JsonObject
        template?.get("amount_minor")?.let {
            exactInteger(it, "payload.template_transaction.amount_minor").requireRange("amount_minor", "1", BIGINT_MAX)
        }
        validateNumericPaths(command, "")
    }

    private fun validateNumericPaths(element: JsonElement, path: String) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val childPath = if (path.isEmpty()) key else "$path.$key"
                if (childPath == "operation_id" || childPath == "request_hash") return@forEach
                validateNumericPaths(value, childPath)
            }
            is JsonArray -> element.forEachIndexed { index, child -> validateNumericPaths(child, "$path[$index]") }
            is JsonPrimitive -> if (!element.isString && element.booleanOrNull == null && element !== JsonNull) {
                require(path in ALLOWED_NUMERIC_PATHS) { "Numeric value is not allowed at $path" }
            }
            JsonNull -> Unit
        }
    }

    private fun exactInteger(element: JsonElement?, path: String): BigDecimal {
        val primitive = element as? JsonPrimitive ?: throw IllegalArgumentException("$path must be a JSON number")
        require(!primitive.isString && primitive.booleanOrNull == null) { "$path must be a JSON number" }
        val value = try { BigDecimal(primitive.content) } catch (exception: NumberFormatException) {
            throw IllegalArgumentException("$path must be a valid exact number", exception)
        }
        require(value.stripTrailingZeros().scale() <= 0) { "$path must be an exact integer" }
        return value
    }

    private fun BigDecimal.requireRange(path: String, minimum: String, maximum: String): BigDecimal {
        require(this >= BigDecimal(minimum) && this <= BigDecimal(maximum)) { "$path is out of range" }
        return this
    }

    private val ALLOWED_NUMERIC_PATHS = setOf(
        "contract_version",
        "expected_revision",
        "payload.reminder_lead_days",
        "payload.interval_count",
        "payload.template_transaction.amount_minor",
    )
}
