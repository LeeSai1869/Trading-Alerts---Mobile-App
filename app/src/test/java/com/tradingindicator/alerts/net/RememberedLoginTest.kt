package com.tradingindicator.alerts.net

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RememberedLoginTest {
    @Test fun rememberedLoginRequestsPersistentSessionAndRestoredClientSendsCookie() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"username":"alice","role":"user"}""")
                .addHeader("Set-Cookie", "tradingbot_session=test-session; Path=/; HttpOnly; Max-Age=2592000"))
            val base = server.url("/").toString().trimEnd('/')
            val result = ApiClient(base).login("alice", "example-password", true)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            val body = JSONObject(request.body.readUtf8())
            assertTrue(body.getBoolean("remember"))
            assertEquals("alice", body.getString("username"))
            assertEquals("/api/login", request.path)
            assertEquals("tradingbot_session=test-session", result.cookie)
            server.enqueue(MockResponse().setBody("""{"username":"alice","role":"user","configured":true,"remembered":true}"""))
            assertTrue(ApiClient(base, result.cookie).hasAuthenticatedSession())
            assertEquals(result.cookie, server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Cookie"))
        }
    }
    @Test fun uncheckedOptionRequestsOrdinarySession() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"username":"alice","role":"user"}""")
                .addHeader("Set-Cookie", "tradingbot_session=temporary; Path=/; HttpOnly"))
            ApiClient(server.url("/").toString()).login("alice", "example-password", false)
            assertFalse(JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()).getBoolean("remember"))
        }
    }
    @Test fun revokedOrExpiredSessionRequiresSignIn() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"role":"guest","configured":true}"""))
            assertFalse(ApiClient(server.url("/").toString(), "tradingbot_session=expired").hasAuthenticatedSession())
        }
    }
}
