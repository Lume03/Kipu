package com.kipu.app.feature.categories.domain.model

/**
 * System categories every owner starts with; the initial setup lets the owner keep or hide each one.
 * Mirrors the root system taxonomy seeded by the backend (stable ids 1–12, icon keys from `icon_key`).
 * Every system root is stored as GENERAL; there is no system income category yet.
 */
object InitialCategoryCatalog {
    data class Entry(val id: String, val name: String, val iconKey: String, val type: CategoryType = CategoryType.GENERAL)

    val entries: List<Entry> = listOf(
        Entry("00000000-0000-0000-0000-000000000001", "Alimentación", "restaurant"),
        Entry("00000000-0000-0000-0000-000000000002", "Transporte", "directions_car"),
        Entry("00000000-0000-0000-0000-000000000003", "Servicios", "receipt"),
        Entry("00000000-0000-0000-0000-000000000004", "Suscripciones", "tv"),
        Entry("00000000-0000-0000-0000-000000000005", "Salud", "medical_services"),
        Entry("00000000-0000-0000-0000-000000000006", "Compras", "shopping_cart"),
        Entry("00000000-0000-0000-0000-000000000007", "Ocio y entretenimiento", "movie"),
        Entry("00000000-0000-0000-0000-000000000008", "Viajes y alojamiento", "flight"),
        Entry("00000000-0000-0000-0000-000000000009", "Finanzas y seguros", "account_balance"),
        Entry("00000000-0000-0000-0000-000000000010", "Educación", "school"),
        Entry("00000000-0000-0000-0000-000000000011", "Mascotas", "pets"),
        Entry("00000000-0000-0000-0000-000000000012", "Estado y trámites", "receipt"),
    )
}
