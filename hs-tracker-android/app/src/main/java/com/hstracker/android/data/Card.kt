package com.hstracker.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Card(
    @SerialName("dbfId") val dbfId: Int,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("cost") val cost: Int? = null,
    @SerialName("attack") val attack: Int? = null,
    @SerialName("health") val health: Int? = null,
    @SerialName("cardClass") val cardClass: String? = null,
    @SerialName("rarity") val rarity: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("set") val set: String? = null,
)
