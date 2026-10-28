package dev.openmacro.core.engine.perform

import android.content.Context
import dev.openmacro.core.model.ActionSpec as A
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * HTTP, UDP and connectivity actions. On the offline flavor the app has no INTERNET permission,
 * so any attempt fails with a clear SecurityException message rather than silently doing nothing.
 */
internal class NetworkActions(@Suppress("unused") private val app: Context) {

    suspend fun perform(spec: A, ctx: ActionContext): ActionOutcome? = when (spec) {
        is A.HttpRequest -> httpRequest(spec, ctx)
        is A.UdpCommand -> udp(spec, ctx)
        is A.ConnectivityCheck -> connectivity(spec, ctx)
        is A.DownloadFile -> download(spec, ctx)
        is A.GetWeather -> getWeather(spec, ctx)
        else -> null
    }

    private suspend fun getWeather(spec: A.GetWeather, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${spec.latitude}&longitude=${spec.longitude}&current=temperature_2m,weather_code"
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            if (connection.responseCode !in 200..299) return@withContext ActionOutcome.Failed("Weather HTTP ${connection.responseCode}")
            val current = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            if (spec.temperature.name.isNotBlank()) ctx.setVariableValue(spec.temperature, current.getDouble("temperature_2m").toString())
            if (spec.condition.name.isNotBlank()) ctx.setVariableValue(spec.condition, weatherText(current.getInt("weather_code")))
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("This build has no internet access (use the online build)")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Weather lookup failed")
        } finally {
            connection?.disconnect()
        }
    }

    /** WMO weather interpretation codes → short description. */
    private fun weatherText(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        in 85..86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    private suspend fun download(spec: A.DownloadFile, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val dest = java.io.File(ctx.render(spec.destination))
            dest.parentFile?.mkdirs()
            connection = (URL(ctx.render(spec.url)).openConnection() as HttpURLConnection).apply {
                connectTimeout = spec.timeoutSeconds * 1000
                readTimeout = spec.timeoutSeconds * 1000
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            if (code !in 200..299) return@withContext ActionOutcome.Failed("HTTP $code")
            connection.inputStream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            if (spec.savedPath.name.isNotBlank()) ctx.setVariableValue(spec.savedPath, dest.absolutePath)
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("This build has no internet access (use the online build)")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Download failed")
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun httpRequest(spec: A.HttpRequest, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        val url = ctx.render(spec.url)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = spec.method.name
                connectTimeout = spec.timeoutSeconds * 1000
                readTimeout = spec.timeoutSeconds * 1000
                instanceFollowRedirects = true
                ctx.render(spec.headers).lineSequence()
                    .mapNotNull { line -> line.split(":", limit = 2).takeIf { it.size == 2 } }
                    .forEach { setRequestProperty(it[0].trim(), ctx.render(it[1].trim())) }
                if (spec.method.name in setOf("POST", "PUT", "PATCH")) {
                    doOutput = true
                    setRequestProperty("Content-Type", spec.contentType)
                    outputStream.use { it.write(ctx.render(spec.body).toByteArray()) }
                }
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (spec.responseBody.name.isNotBlank()) ctx.setVariableValue(spec.responseBody, body)
            if (spec.responseCode.name.isNotBlank()) ctx.setVariableValue(spec.responseCode, code.toString())
            if (code in 200..399) ActionOutcome.Success else ActionOutcome.Failed("HTTP $code")
        } catch (e: SecurityException) {
            ActionOutcome.Failed("This build has no internet access (use the online build)")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Request failed")
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun udp(spec: A.UdpCommand, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        try {
            val payload = ctx.render(spec.payload).toByteArray()
            val address = InetAddress.getByName(ctx.render(spec.host))
            DatagramSocket().use { socket ->
                socket.send(DatagramPacket(payload, payload.size, address, spec.port))
            }
            ActionOutcome.Success
        } catch (e: SecurityException) {
            ActionOutcome.Failed("This build has no internet access (use the online build)")
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "UDP send failed")
        }
    }

    private suspend fun connectivity(spec: A.ConnectivityCheck, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val reachable = try {
            connection = (URL(ctx.render(spec.url)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "HEAD"
            }
            connection.responseCode in 200..399
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
        if (spec.result.name.isNotBlank()) ctx.setVariableValue(spec.result, reachable.toString())
        ActionOutcome.Success
    }
}
