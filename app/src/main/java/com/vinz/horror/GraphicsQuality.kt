package com.vinz.horror

enum class GraphicsQuality(
    val label: String,
    val fogDensity: Float,
    val useNormalMap: Boolean,
    val drawDistanceCells: Int,
    val propDensity: Float
) {
    LOW("Low", 0.9f, false, 5, 0.2f),
    MEDIUM("Medium", 0.55f, true, 8, 0.5f),
    EXTREME("Extreme", 0.32f, true, 14, 1.0f);

    companion object {
        fun fromOrdinalSafe(i: Int): GraphicsQuality = values().getOrElse(i) { MEDIUM }
    }
}
