package com.kipu.app.feature.categories.presentation

import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

fun resolveCategoryIcon(iconId: String): ImageVector {
    return when (iconId.trim().lowercase()) {
        "delivery_dining" -> Icons.Default.DeliveryDining
        "toll" -> Icons.Default.Toll
        "local_fire_department" -> Icons.Default.LocalFireDepartment
        "cloud" -> Icons.Default.Cloud
        "smart_toy" -> Icons.Default.SmartToy
        "terminal" -> Icons.Default.Terminal
        "shopping_bag" -> Icons.Default.ShoppingBag
        "confirmation_number" -> Icons.Default.ConfirmationNumber
        "hotel" -> Icons.Default.Hotel
        "security" -> Icons.Default.Security
        "assignment" -> Icons.Default.Assignment
        "tv", "streaming", "video" -> Icons.Default.Tv
        "music_note", "musica", "música", "audio" -> Icons.Default.MusicNote
        "directions_car", "car", "auto", "transporte", "transport" -> Icons.Default.DirectionsCar
        "content_cut", "barberia", "peluqueria", "corte" -> Icons.Default.ContentCut
        "receipt", "recibo", "factura", "servicios", "services" -> Icons.Default.Receipt
        "shopping_cart", "cart", "compras", "supermercado" -> Icons.Default.ShoppingCart
        "restaurant", "food", "comida", "alimentacion", "alimentación" -> Icons.Default.Restaurant
        "fitness_center", "gym", "gimnasio", "deporte" -> Icons.Default.FitnessCenter
        "local_cafe", "cafe", "cafeteria" -> Icons.Default.LocalCafe
        "storefront", "tienda" -> Icons.Default.Storefront
        "directions_transit", "metro", "bus" -> Icons.Default.DirectionsTransit
        "local_gas_station", "gasolina", "combustible" -> Icons.Default.LocalGasStation
        "flight", "vuelo", "viaje", "avion" -> Icons.Default.Flight
        "home", "casa", "hogar" -> Icons.Default.Home
        "electric_bolt", "luz", "electricidad" -> Icons.Default.ElectricBolt
        "water_drop", "agua" -> Icons.Default.WaterDrop
        "wifi", "internet" -> Icons.Default.Wifi
        "movie", "cine", "pelicula" -> Icons.Default.Movie
        "medical_services", "salud", "medico", "farmacia" -> Icons.Default.MedicalServices
        "school", "educacion", "colegio", "universidad" -> Icons.Default.School
        "spa", "masaje", "bienestar" -> Icons.Default.Spa
        "pets", "mascota", "veterinaria" -> Icons.Default.Pets
        "sports_esports", "juegos", "gamer" -> Icons.Default.SportsEsports
        "devices", "laptop", "tecnologia", "software" -> Icons.Default.Devices
        "cleaning_services", "limpieza" -> Icons.Default.CleaningServices
        "savings", "ahorro" -> Icons.Default.Savings
        "payments", "pago", "sueldo" -> Icons.Default.Payments
        "work", "trabajo" -> Icons.Default.Work
        "folder" -> Icons.Default.Folder
        "credit_card" -> Icons.Default.CreditCard
        "account_balance" -> Icons.Default.AccountBalance
        "account_balance_wallet" -> Icons.Default.AccountBalanceWallet
        "card_giftcard", "regalo" -> Icons.Default.CardGiftcard
        "favorite" -> Icons.Default.Favorite
        "pedal_bike", "bici" -> Icons.Default.PedalBike
        "star" -> Icons.Default.Star
        "label", "tag", "etiqueta", "category", "categoria", "categoría" -> Icons.Default.Label
        else -> Icons.Default.Label
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF0F766E)
    }
}
