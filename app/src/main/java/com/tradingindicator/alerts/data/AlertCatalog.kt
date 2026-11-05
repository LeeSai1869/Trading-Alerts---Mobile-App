package com.tradingindicator.alerts.data

import org.json.JSONArray
import org.json.JSONObject

data class AlertCatalog(val symbols: List<String>, val intervals: List<String>) {
    fun toJson(): String = JSONObject()
        .put("symbols", JSONArray(symbols)).put("intervals", JSONArray(intervals)).toString()

    companion object {
        fun parse(json: JSONObject): AlertCatalog {
            fun entries(key: String): List<String> {
                val array = json.getJSONArray(key)
                return (0 until array.length()).map { array.getString(it).trim() }
                    .filter { it.isNotEmpty() }.distinct()
            }
            return AlertCatalog(entries("symbols").sorted(), entries("intervals"))
        }
    }
}
