package top.lvziwang.ntfyhub.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Response
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import top.lvziwang.ntfyhub.model.BootstrapResponse
import top.lvziwang.ntfyhub.model.MessagesResponse

interface HubApi {
    @POST("api/login")
    suspend fun login(@Body request: RequestBody): Response<Unit>

    @GET("api/bootstrap")
    suspend fun bootstrap(): BootstrapResponse

    @GET("api/messages")
    suspend fun messages(
        @Query("topic") topic: String? = null,
        @Query("tag") tags: List<String> = emptyList(),
        @Query("q") query: String? = null,
        @Query("beforeId") beforeId: String? = null,
        @Query("limit") limit: Int = 80
    ): MessagesResponse

    companion object {
        fun create(sessionManager: SessionManager): HubApi {
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

            val client = OkHttpClient.Builder()
                .cookieJar(sessionManager)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val baseUrl = sessionManager.baseUrl ?: error("Server URL is not configured")
                    val original = request.url
                    val resolvedUrl = HttpUrl.parseCompat(baseUrl)?.newBuilder()
                        ?.addPathSegments(original.encodedPath.removePrefix("/"))
                        ?.apply {
                            original.queryParameterNames.forEach { name ->
                                original.queryParameterValues(name).forEach { value ->
                                    addQueryParameter(name, value)
                                }
                            }
                        }
                        ?.build()
                        ?: error("Invalid server URL")

                    chain.proceed(request.newBuilder().url(resolvedUrl).build())
                }
                .build()

            return Retrofit.Builder()
                .baseUrl("https://placeholder.invalid/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(HubApi::class.java)
        }
    }
}

class SessionManager : CookieJar {
    @Volatile
    var baseUrl: String? = null

    private val cookies = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty()

    fun hasSessionForBaseUrl(): Boolean {
        val host = baseUrl?.let { HttpUrl.parseCompat(it)?.host } ?: return false
        return cookies[host].isNullOrEmpty().not()
    }

    fun sessionCookieValueForBaseUrl(): Pair<String, String>? {
        val host = baseUrl?.let { HttpUrl.parseCompat(it)?.host } ?: return null
        val cookie = cookies[host]?.firstOrNull { it.name == "ntfy_message_hub_session" } ?: return null
        return host to cookie.value
    }

    fun restoreSession(host: String, cookieValue: String) {
        cookies[host] = listOf(
            Cookie.Builder()
                .name("ntfy_message_hub_session")
                .value(cookieValue)
                .domain(host)
                .path("/")
                .httpOnly()
                .build()
        )
    }

    fun clear() {
        cookies.clear()
    }
}

private fun HttpUrl.Companion.parseCompat(url: String): HttpUrl? = try {
    url.toHttpUrl()
} catch (_: IllegalArgumentException) {
    null
}
