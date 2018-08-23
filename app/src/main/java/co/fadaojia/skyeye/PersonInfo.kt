package co.fadaojia.skyeye

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable


@Serializable
internal data class PersonInfo(
        val Person_id: Int = 0,
        var Person_Name: String? = null,
        @Optional
        var Person_HeadUrl: String? = null,
        @Optional
        val Age: Int = 0,
        @Optional
        val Gender: Int = 0,
        @Optional
        val Glasses: Int = 0,
        @Optional
        val AgeConfidence: Double = 0.toDouble(),
        @Optional
        val GenderConfidence: Double = 0.toDouble(),
        @Optional
        val GlassesConfidence: Double = 0.toDouble()
)