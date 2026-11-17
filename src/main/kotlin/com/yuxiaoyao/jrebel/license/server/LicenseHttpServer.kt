package com.yuxiaoyao.jrebel.license.server

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 内嵌 HTTP 服务，逻辑对应 Go 版 [server.go] 与 [jrebelhandler.go]。
 * 只监听回环地址，避免把激活服务暴露到局域网。
 */
class LicenseHttpServer(private val configProvider: () -> ServerConfig) {

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private var boundPort: Int = 0

    @Synchronized
    fun start(port: Int) {
        stop()

        val httpServer = HttpServer.create(InetSocketAddress(LOOPBACK, port), 0)
        val pool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "JRebelLicenseServer").apply { isDaemon = true }
        }
        httpServer.executor = pool
        httpServer.createContext("/") { exchange -> handle(exchange) }
        httpServer.start()

        server = httpServer
        executor = pool
        boundPort = port
        LOG.info("JRebel license server started on $LOOPBACK:$port")
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        executor?.shutdownNow()
        server = null
        executor = null
        boundPort = 0
    }

    val running: Boolean
        @Synchronized get() = server != null

    val port: Int
        @Synchronized get() = boundPort

    private fun handle(exchange: HttpExchange) {
        try {
            logRequest(exchange)
            when (exchange.requestURI.path) {
                "/jrebel/leases", "/agent/leases" -> handleLeases(exchange)
                "/jrebel/leases/1", "/agent/leases/1" -> handleLeases1(exchange)
                "/jrebel/validate-connection" ->
                    write(exchange, 200, JSON_CONTENT_TYPE, LeaseResponses.validateConnection())

                "/rpc/ping.action" -> handlePing(exchange)
                "/rpc/obtainTicket.action" -> handleObtainTicket(exchange)
                "/rpc/releaseTicket.action" -> handleReleaseTicket(exchange)
                else -> handleIndex(exchange)
            }
        } catch (e: IOException) {
            LOG.debug("Connection closed while handling ${exchange.requestURI}", e)
        } catch (e: Exception) {
            LOG.warn("Handle request failed: ${exchange.requestURI}", e)
            runCatching { write(exchange, 500, TEXT_CONTENT_TYPE, e.message ?: "error") }
        } finally {
            exchange.close()
        }
    }

    private fun handleIndex(exchange: HttpExchange) {
        val config = configProvider()
        val host = if (config.exportHost.isNotBlank()) {
            "${config.exportSchema}://${config.exportHost}"
        } else {
            val requestHost = exchange.requestHeaders.getFirst("Host") ?: "$LOOPBACK:${config.port}"
            "${config.exportSchema}://$requestHost"
        }
        write(exchange, 200, HTML_CONTENT_TYPE, IndexPage.render(host, config.uuid))
    }

    private fun handleLeases(exchange: HttpExchange) {
        val body = LeaseResponses.lease(readParams(exchange), configProvider())
        if (body == null) {
            write(exchange, 403, JSON_CONTENT_TYPE, null)
        } else {
            write(exchange, 200, JSON_CONTENT_TYPE, body)
        }
    }

    private fun handleLeases1(exchange: HttpExchange) {
        write(exchange, 200, JSON_CONTENT_TYPE, LeaseResponses.lease1(readParams(exchange)))
    }

    private fun handlePing(exchange: HttpExchange) {
        val salt = readParams(exchange)["salt"].orEmpty()
        if (salt.isEmpty()) {
            write(exchange, 403, HTML_CONTENT_TYPE, null)
            return
        }
        write(exchange, 200, HTML_CONTENT_TYPE, LeaseResponses.ping(salt))
    }

    private fun handleObtainTicket(exchange: HttpExchange) {
        val params = readParams(exchange)
        val salt = params["salt"].orEmpty()
        val userName = params["userName"].orEmpty()
        if (salt.isEmpty() || userName.isEmpty()) {
            write(exchange, 403, JSON_CONTENT_TYPE, null)
            return
        }
        write(exchange, 200, JSON_CONTENT_TYPE, LeaseResponses.obtainTicket(salt, userName))
    }

    private fun handleReleaseTicket(exchange: HttpExchange) {
        val salt = readParams(exchange)["salt"].orEmpty()
        if (salt.isEmpty()) {
            write(exchange, 403, HTML_CONTENT_TYPE, null)
            return
        }
        write(exchange, 200, HTML_CONTENT_TYPE, LeaseResponses.releaseTicket(salt))
    }

    private fun write(exchange: HttpExchange, status: Int, contentType: String, body: String?) {
        exchange.responseHeaders.set("Content-Type", contentType)
        if (body == null) {
            exchange.sendResponseHeaders(status, -1)
            return
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun readParams(exchange: HttpExchange): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        exchange.requestURI.rawQuery?.let { result.putAll(parseForm(it)) }

        val body = exchange.requestBody.readBytes()
        if (body.isNotEmpty()) {
            result.putAll(parseForm(String(body, StandardCharsets.UTF_8)))
        }
        return result
    }

    private fun parseForm(raw: String): Map<String, String> {
        if (raw.isEmpty()) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) {
                continue
            }
            val index = pair.indexOf('=')
            val key = if (index >= 0) pair.substring(0, index) else pair
            val value = if (index >= 0) pair.substring(index + 1) else ""
            result[URLDecoder.decode(key, StandardCharsets.UTF_8)] =
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
        return result
    }

    private fun logRequest(exchange: HttpExchange) {
        val query = exchange.requestURI.rawQuery?.let { "?$it" }.orEmpty()
        val remote = exchange.remoteAddress?.toString() ?: "-"
        val userAgent = exchange.requestHeaders.getFirst("User-Agent") ?: "-"
        LOG.info("--> ${exchange.requestMethod} ${exchange.requestURI.path}$query [$remote] [$userAgent]")
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        const val HTML_CONTENT_TYPE = "text/html; charset=utf-8"
        const val TEXT_CONTENT_TYPE = "text/plain; charset=utf-8"
        val LOG: Logger = Logger.getInstance(LicenseHttpServer::class.java)
    }
}
