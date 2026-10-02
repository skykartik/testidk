package com.bubbly.calc

data class Theme(val name: String, val a: Long, val b: Long, val c: Long, val d: Long)

// four colors per theme, matching the Windows palettes (ARGB hex as Long for Compose Color(Long))
val THEMES = listOf(
    Theme("Bubblegum", 0xFFFF8AD8L, 0xFFE0309EL, 0xFFF4FF7AL, 0xFFB8E63AL),
    Theme("Aqua",      0xFF7DF9FFL, 0xFF1F9BEAL, 0xFFFFD98AL, 0xFFFF8A3DL),
    Theme("Grape",     0xFFC7A6FFL, 0xFF7A45E0L, 0xFF8CFFD8L, 0xFF25C9A0L),
    Theme("Sunset",    0xFFFFC58AL, 0xFFFF5D7AL, 0xFFB8F7FFL, 0xFF4DB8FFL),
)

enum class CatKind { LINEAR, TEMP, CURRENCY }

data class Cat(val name: String, val kind: CatKind, val a: Int, val b: Int, val units: List<String>, val factors: DoubleArray? = null)

val CATS = listOf(
    Cat("Currency", CatKind.CURRENCY, 0, 1, listOf(
        "USD", "INR", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "AED",
        "SGD", "SAR", "KRW", "BRL", "MXN", "ZAR", "NZD", "THB", "HKD")),
    Cat("Length", CatKind.LINEAR, 1, 4, listOf("m", "km", "cm", "mm", "mi", "yd", "ft", "in"),
        doubleArrayOf(1.0, 1000.0, 0.01, 0.001, 1609.344, 0.9144, 0.3048, 0.0254)),
    Cat("Weight", CatKind.LINEAR, 0, 3, listOf("kg", "g", "mg", "lb", "oz", "t", "st"),
        doubleArrayOf(1.0, 0.001, 1e-6, 0.45359237, 0.028349523, 1000.0, 6.35029318)),
    Cat("Temperature", CatKind.TEMP, 0, 1, listOf("\u00b0C", "\u00b0F", "K")),
    Cat("Speed", CatKind.LINEAR, 1, 2, listOf("m/s", "km/h", "mph", "kn"),
        doubleArrayOf(1.0, 1 / 3.6, 0.44704, 0.514444)),
    Cat("Volume", CatKind.LINEAR, 0, 2, listOf("L", "mL", "gal", "cup", "fl oz", "m\u00b3"),
        doubleArrayOf(1.0, 0.001, 3.785411784, 0.2365882365, 0.0295735296, 1000.0)),
    Cat("Area", CatKind.LINEAR, 0, 3, listOf("m\u00b2", "km\u00b2", "ha", "acre", "ft\u00b2", "yd\u00b2", "mi\u00b2"),
        doubleArrayOf(1.0, 1e6, 1e4, 4046.8564224, 0.09290304, 0.83612736, 2589988.110336)),
    Cat("Data", CatKind.LINEAR, 3, 2, listOf("B", "KB", "MB", "GB", "TB", "bit"),
        doubleArrayOf(1.0, 1024.0, 1048576.0, 1073741824.0, 1099511627776.0, 0.125)),
)

/** Offline fallback rates (USD base) - used whenever a live fetch isn't available. Not for real financial use. */
fun fallbackRates(): MutableMap<String, Double> {
    val c = listOf("USD", "INR", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "AED",
        "SGD", "SAR", "KRW", "BRL", "MXN", "ZAR", "NZD", "THB", "HKD")
    val r = listOf(1.0, 83.5, 0.92, 0.79, 150.0, 1.52, 1.36, 0.88, 7.2, 3.6725,
        1.35, 3.75, 1340.0, 5.0, 17.5, 18.5, 1.65, 36.0, 7.8)
    return c.zip(r).toMap().toMutableMap()
}

fun convert(cat: Cat, fi: Int, ti: Int, v: Double, rates: Map<String, Double>): Double {
    return when (cat.kind) {
        CatKind.TEMP -> {
            val f = cat.units[fi]; val t = cat.units[ti]
            val k = when (f) { "\u00b0C" -> v + 273.15; "\u00b0F" -> (v - 32) * 5 / 9 + 273.15; else -> v }
            when (t) { "\u00b0C" -> k - 273.15; "\u00b0F" -> (k - 273.15) * 9 / 5 + 32; else -> k }
        }
        CatKind.CURRENCY -> {
            val a = rates[cat.units[fi]] ?: 1.0
            val b = rates[cat.units[ti]] ?: 1.0
            v / a * b
        }
        CatKind.LINEAR -> {
            val factors = cat.factors!!
            v * factors[fi] / factors[ti]
        }
    }
}
