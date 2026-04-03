package world.respect.fidelio.data.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class XapiReporter(private val client: OkHttpClient) {
    private val TAG = "XapiReporter"

    fun reportMissionProgress(
        endpoint: String,
        auth: String,
        givenName: String?,
        gameId: String,
        gameTitle: String,
        score: Int,
        maxScore: Int,
        success: Boolean
    ) {
        val statement = JSONObject().apply {
            put("actor", JSONObject().apply {
                put("name", givenName ?: "Student")
                put("openid", auth) // Using token as identifier for now
            })
            put("verb", JSONObject().apply {
                put("id", "http://adlnet.gov/expapi/verbs/completed")
                put("display", JSONObject().apply { put("en-US", "completed") })
            })
            put("object", JSONObject().apply {
                put("id", "https://learningcloud.et/games/$gameId")
                put("definition", JSONObject().apply {
                    put("name", JSONObject().apply { put("en-US", gameTitle) })
                    put("description", JSONObject().apply { put("en-US", "Educational game: $gameTitle") })
                })
            })
            put("result", JSONObject().apply {
                put("score", JSONObject().apply {
                    put("min", 0)
                    put("max", maxScore)
                    put("raw", score)
                    put("scaled", if (maxScore > 0) score.toDouble() / maxScore else 0.0)
                })
                put("completion", true)
                put("success", success)
            })
        }

        val body = statement.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $auth")
            .addHeader("X-Experience-API-Version", "1.0.3")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "xAPI reporting failed for $gameId", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    Log.d(TAG, "xAPI reporting success for $gameId")
                } else {
                    Log.e(TAG, "xAPI reporting error: ${response.code} ${response.body?.string()}")
                }
            }
        })
    }
}
