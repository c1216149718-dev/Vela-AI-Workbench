package com.deepseek.widget.data.provider

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudConnectorParsingTest {
    @Test
    fun tokenHubParsesDailyModelSeriesIncludingCacheTokens() = runBlocking {
        val client = jsonClient(
            """{
              "Response": {
                "Timestamps": [1786320000],
                "TopList": [{
                  "Name": "deepseek-v3.2",
                  "Series": {
                    "TotalToken": "[2100]",
                    "InputTotalToken": "[1200]",
                    "OutputTotalToken": "[800]",
                    "CacheTotalToken": "[100]"
                  }
                }],
                "Total": 1,
                "Limit": 10
              }
            }"""
        )
        val connector = TencentTokenHubConnector(client)
        val result = connector.syncDailyUsage(
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 10),
            mapOf("secret_id" to "id", "secret_key" to "secret")
        ) as ProviderResult.Supported

        val point = result.value.items.single()
        assertEquals("deepseek-v3.2", point.model)
        assertEquals(2_100L, point.totalTokens)
        assertEquals(100L, point.cachedTokens)
        assertEquals(null, result.value.nextCursor)
    }

    @Test
    fun qianfanParsesOfficialServiceMetricAndAdvancesOneDay() = runBlocking {
        val client = jsonClient(
            """{
              "requestId": "request-1",
              "result": {
                "serviceList": [{
                  "serviceId": "svc-1",
                  "serviceName": "ernie-4.5",
                  "appList": [{
                    "appId": "app-1",
                    "metric": {
                      "inputTokensTotal": 900,
                      "outputTokensTotal": 721,
                      "tokensTotal": 1621,
                      "callTotal": 675
                    }
                  }]
                }]
              }
            }"""
        )
        val connector = BaiduQianfanConnector(client)
        val result = connector.syncDailyUsage(
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 11),
            mapOf("access_key" to "ak", "secret_key" to "sk")
        ) as ProviderResult.Supported

        val point = result.value.items.single()
        assertEquals(LocalDate.of(2026, 8, 10), point.date)
        assertEquals("ernie-4.5", point.model)
        assertEquals(675L, point.requests)
        assertEquals(1_621L, point.totalTokens)
        assertEquals("2026-08-11", result.value.nextCursor)
    }

    @Test
    fun bailianParsesCloudAccountBalanceWithoutTreatingItAsProviderCredit() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                assertTrue(chain.request().url.queryParameter("Signature").orEmpty().isNotBlank())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{
                          "Code": "200",
                          "Success": true,
                          "Data": {
                            "AvailableAmount": "1000.19",
                            "AvailableCashAmount": "0.00",
                            "Currency": "CNY"
                          }
                        }""".toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()
        val connector = AlibabaBailianConnector(client)
        val result = connector.syncBalance(mapOf("access_key" to "ak", "secret_key" to "sk")) as ProviderResult.Supported

        val balance = result.value.single()
        assertEquals("CNY", balance.currency)
        assertEquals("1000.19", balance.amount.toPlainString())
        assertTrue(balance.cloudAccount)
    }

    private fun jsonClient(body: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            assertTrue(chain.request().header("Authorization").orEmpty().isNotBlank())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()
}
