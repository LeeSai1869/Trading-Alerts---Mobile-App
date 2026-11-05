package com.tradingindicator.alerts.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlertCatalogTest {
    @Test fun loadsEveryServerCoinAndPreservesTimeframeCaseAndOrder() {
        val result = AlertCatalog.parse(JSONObject("""{"symbols":["SOLUSDT","MORPHOUSDT","BTCUSDT","XRPUSDT","ASTERUSDT","HYPEUSDT","BTCUSDT",""],"intervals":["1m","3m","5m","1d","1M"]}"""))
        assertEquals(6, result.symbols.size)
        assertEquals(listOf("ASTERUSDT","BTCUSDT","HYPEUSDT","MORPHOUSDT","SOLUSDT","XRPUSDT"), result.symbols)
        assertEquals(listOf("1m","3m","5m","1d","1M"), result.intervals)
        assertEquals(result, AlertCatalog.parse(JSONObject(result.toJson())))
    }
    @Test fun emptyServerListStaysEmpty() {
        assertTrue(AlertCatalog.parse(JSONObject("""{"symbols":[],"intervals":[]}""")).symbols.isEmpty())
    }
    @Test(expected = org.json.JSONException::class) fun invalidResponseDoesNotBecomeFakeDefaultList() {
        AlertCatalog.parse(JSONObject("""{"error":"Sign in required"}"""))
    }
}
