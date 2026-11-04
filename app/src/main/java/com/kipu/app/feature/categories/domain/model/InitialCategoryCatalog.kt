package com.kipu.app.feature.categories.domain.model

/** Stable backend taxonomy; these system branches classify expenses, never custom income roots. */
object InitialCategoryCatalog {
    data class Entry(
        val id: String,
        val name: String,
        val iconKey: String,
        val color: String,
        val parentId: String? = null,
        val type: CategoryType = CategoryType.EXPENSE,
    )

    val allEntries: List<Entry> = listOf(
        Entry("00000000-0000-0000-0000-000000000001", "Alimentación", "restaurant", "#D97706"),
        Entry("00000000-0000-0000-0000-000000000002", "Transporte", "directions_car", "#0284C7"),
        Entry("00000000-0000-0000-0000-000000000003", "Servicios", "receipt", "#7C3AED"),
        Entry("00000000-0000-0000-0000-000000000004", "Suscripciones", "tv", "#8B5CF6"),
        Entry("00000000-0000-0000-0000-000000000005", "Salud", "medical_services", "#E85D5D"),
        Entry("00000000-0000-0000-0000-000000000006", "Compras", "shopping_cart", "#EC4899"),
        Entry("00000000-0000-0000-0000-000000000007", "Ocio y entretenimiento", "movie", "#8B5CF6"),
        Entry("00000000-0000-0000-0000-000000000008", "Viajes y alojamiento", "flight", "#0284C7"),
        Entry("00000000-0000-0000-0000-000000000009", "Finanzas y seguros", "account_balance", "#0F766E"),
        Entry("00000000-0000-0000-0000-000000000010", "Educación", "school", "#F59E0B"),
        Entry("00000000-0000-0000-0000-000000000011", "Mascotas", "pets", "#10B981"),
        Entry("00000000-0000-0000-0000-000000000012", "Estado y trámites", "receipt", "#475569"),
        Entry("00000000-0000-0000-0000-000000000101", "Supermercados", "shopping_cart", "#D97706", "00000000-0000-0000-0000-000000000001"),
        Entry("00000000-0000-0000-0000-000000000102", "Tiendas de conveniencia", "storefront", "#D97706", "00000000-0000-0000-0000-000000000001"),
        Entry("00000000-0000-0000-0000-000000000103", "Tiendas de descuento y proximidad", "storefront", "#D97706", "00000000-0000-0000-0000-000000000001"),
        Entry("00000000-0000-0000-0000-000000000104", "Fast food", "restaurant", "#D97706", "00000000-0000-0000-0000-000000000001"),
        Entry("00000000-0000-0000-0000-000000000105", "Cafeterías", "local_cafe", "#D97706", "00000000-0000-0000-0000-000000000001"),
        Entry("00000000-0000-0000-0000-000000000106", "Delivery y agregadores", "delivery_dining", "#D97706", "00000000-0000-0000-0000-000000000001"),
        Entry("00000000-0000-0000-0000-000000000201", "Taxi y movilidad por aplicativo", "directions_car", "#0284C7", "00000000-0000-0000-0000-000000000002"),
        Entry("00000000-0000-0000-0000-000000000202", "Combustibles y grifos", "local_gas_station", "#0284C7", "00000000-0000-0000-0000-000000000002"),
        Entry("00000000-0000-0000-0000-000000000203", "Transporte público urbano", "directions_transit", "#0284C7", "00000000-0000-0000-0000-000000000002"),
        Entry("00000000-0000-0000-0000-000000000204", "Peajes", "toll", "#0284C7", "00000000-0000-0000-0000-000000000002"),
        Entry("00000000-0000-0000-0000-000000000205", "Aerolíneas", "flight", "#0284C7", "00000000-0000-0000-0000-000000000002"),
        Entry("00000000-0000-0000-0000-000000000301", "Electricidad", "electric_bolt", "#7C3AED", "00000000-0000-0000-0000-000000000003"),
        Entry("00000000-0000-0000-0000-000000000302", "Agua potable y saneamiento", "water_drop", "#7C3AED", "00000000-0000-0000-0000-000000000003"),
        Entry("00000000-0000-0000-0000-000000000303", "Gas natural", "local_fire_department", "#7C3AED", "00000000-0000-0000-0000-000000000003"),
        Entry("00000000-0000-0000-0000-000000000304", "Telecomunicaciones e internet", "wifi", "#7C3AED", "00000000-0000-0000-0000-000000000003"),
        Entry("00000000-0000-0000-0000-000000000401", "Streaming de video", "tv", "#8B5CF6", "00000000-0000-0000-0000-000000000004"),
        Entry("00000000-0000-0000-0000-000000000402", "Streaming de música", "music_note", "#8B5CF6", "00000000-0000-0000-0000-000000000004"),
        Entry("00000000-0000-0000-0000-000000000403", "Almacenamiento en la nube", "cloud", "#8B5CF6", "00000000-0000-0000-0000-000000000004"),
        Entry("00000000-0000-0000-0000-000000000404", "Inteligencia artificial", "smart_toy", "#8B5CF6", "00000000-0000-0000-0000-000000000004"),
        Entry("00000000-0000-0000-0000-000000000405", "Productividad y software", "terminal", "#8B5CF6", "00000000-0000-0000-0000-000000000004"),
        Entry("00000000-0000-0000-0000-000000000406", "Videojuegos", "sports_esports", "#8B5CF6", "00000000-0000-0000-0000-000000000004"),
        Entry("00000000-0000-0000-0000-000000000501", "Farmacias y boticas", "medical_services", "#E85D5D", "00000000-0000-0000-0000-000000000005"),
        Entry("00000000-0000-0000-0000-000000000502", "Gimnasios", "fitness_center", "#E85D5D", "00000000-0000-0000-0000-000000000005"),
        Entry("00000000-0000-0000-0000-000000000601", "Tiendas por departamento", "shopping_bag", "#EC4899", "00000000-0000-0000-0000-000000000006"),
        Entry("00000000-0000-0000-0000-000000000602", "Mejoramiento del hogar", "home", "#EC4899", "00000000-0000-0000-0000-000000000006"),
        Entry("00000000-0000-0000-0000-000000000603", "Marketplaces y comercio electrónico", "shopping_cart", "#EC4899", "00000000-0000-0000-0000-000000000006"),
        Entry("00000000-0000-0000-0000-000000000701", "Cine", "movie", "#8B5CF6", "00000000-0000-0000-0000-000000000007"),
        Entry("00000000-0000-0000-0000-000000000702", "Eventos y entradas", "confirmation_number", "#8B5CF6", "00000000-0000-0000-0000-000000000007"),
        Entry("00000000-0000-0000-0000-000000000801", "Hoteles y alojamiento", "hotel", "#0284C7", "00000000-0000-0000-0000-000000000008"),
        Entry("00000000-0000-0000-0000-000000000901", "Bancos", "account_balance", "#0F766E", "00000000-0000-0000-0000-000000000009"),
        Entry("00000000-0000-0000-0000-000000000902", "Billeteras y medios de pago", "account_balance_wallet", "#0F766E", "00000000-0000-0000-0000-000000000009"),
        Entry("00000000-0000-0000-0000-000000000903", "Seguros", "security", "#0F766E", "00000000-0000-0000-0000-000000000009"),
        Entry("00000000-0000-0000-0000-000000000904", "Pasarelas y servicios financieros", "payments", "#0F766E", "00000000-0000-0000-0000-000000000009"),
        Entry("00000000-0000-0000-0000-000000001001", "Universidades e institutos", "school", "#F59E0B", "00000000-0000-0000-0000-000000000010"),
        Entry("00000000-0000-0000-0000-000000001002", "Cursos y formación", "school", "#F59E0B", "00000000-0000-0000-0000-000000000010"),
        Entry("00000000-0000-0000-0000-000000001101", "Veterinarias", "pets", "#10B981", "00000000-0000-0000-0000-000000000011"),
        Entry("00000000-0000-0000-0000-000000001102", "Tiendas de mascotas", "pets", "#10B981", "00000000-0000-0000-0000-000000000011"),
        Entry("00000000-0000-0000-0000-000000001201", "Impuestos y tasas", "receipt", "#475569", "00000000-0000-0000-0000-000000000012"),
        Entry("00000000-0000-0000-0000-000000001202", "Trámites y documentos", "assignment", "#475569", "00000000-0000-0000-0000-000000000012"),
    )

    val entries: List<Entry> = allEntries.filter { it.parentId == null }
    private val byId = allEntries.associateBy { it.id }
    fun find(id: String): Entry? = byId[id]

    /** Older servers may still return GENERAL until the catalog migration is deployed. */
    fun resolvedType(id: String, parentId: String?, origin: String, stored: String): String =
        if ((origin == "SYSTEM" && id in byId) || parentId?.let { byId[it]?.parentId == null && it in byId } == true)
            CategoryType.EXPENSE.name else stored
}
