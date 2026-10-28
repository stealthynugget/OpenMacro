package dev.openmacro.core.engine.service

import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.model.TriggerEvent
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A tiny HTTP/1.1 server (one shared instance for all webhook triggers). A request to
 * `/<token>` emits a [TriggerEvent.WebhookReceived] carrying the token, method and body. Binding
 * a socket needs the INTERNET permission, so this only functions on the online flavor.
 */
internal object WebhookServer {
    const val PORT = 8585

    fun open(emit: (TriggerEvent) -> Unit): Subscription {
        val server = try {
            ServerSocket(PORT)
        } catch (e: Exception) {
            EngineLog.w("Webhook server could not bind port $PORT (needs the online build)", e)
            return Subscription {}
        }
        val worker = thread(name = "openmacro-webhook", isDaemon = true) {
            EngineLog.i("Webhook server listening on $PORT")
            while (!server.isClosed) {
                try {
                    server.accept().use { handle(it, emit) }
                } catch (e: Exception) {
                    if (!server.isClosed) EngineLog.w("Webhook accept failed", e)
                }
            }
        }
        return Subscription {
            runCatching { server.close() }
            worker.interrupt()
        }
    }

    private fun handle(socket: Socket, emit: (TriggerEvent) -> Unit) {
        val input = socket.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return respond(socket, 400, "Bad Request")
        val parts = requestLine.split(" ")
        if (parts.size < 2) return respond(socket, 400, "Bad Request")
        val method = parts[0].uppercase()
        val token = parts[1].trimStart('/').substringBefore('?').substringBefore('/')

        var contentLength = 0
        while (true) {
            val header = input.readLine() ?: break
            if (header.isBlank()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val body = if (contentLength > 0) {
            val buffer = CharArray(contentLength.coerceAtMost(1_000_000))
            val read = input.read(buffer)
            if (read > 0) String(buffer, 0, read) else ""
        } else {
            ""
        }

        if (token.isNotBlank()) emit(TriggerEvent.WebhookReceived(token, method, body))
        respond(socket, 200, "OK")
    }

    private fun respond(socket: Socket, code: Int, message: String) {
        runCatching {
            val body = "$message\n"
            socket.getOutputStream().apply {
                write("HTTP/1.1 $code $message\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray())
                flush()
            }
        }
    }
}
