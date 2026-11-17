package com.yuxiaoyao.jrebel.license.server

import com.yuxiaoyao.jrebel.license.settings.LicenseServerSettings
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.random.Random

/**
 * 各个接口的响应体，字段与 Go 版 [jrebelstruct.go] 保持一致。
 */
object LeaseResponses {

    private const val SERVER_VERSION = "3.2.4"
    private const val SERVER_PROTOCOL_VERSION = "1.1"
    private const val SERVER_GUID = "a1b4aea8-b031-4302-b602-670a990272cb"
    private const val LICENSE_VALID_FROM = 1490544001000L
    private const val LICENSE_VALID_UNTIL = 1891839999000L
    private const val DEFAULT_OFFLINE_DAYS = 7L
    private const val RANDOM_CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"

    /** 返回 null 表示参数缺失，需要回 403。 */
    fun lease(params: Map<String, String>, config: ServerConfig): String? {
        val clientRandomness = params["randomness"].orEmpty()
        val username = params["username"].orEmpty()
        val guid = params["guid"].orEmpty()
        if (clientRandomness.isEmpty() || username.isEmpty() || guid.isEmpty()) {
            return null
        }

        val offline = isOffline(params, config)
        val serverRandomness = newServerRandomness()

        var validFrom = 0L
        var validUntil = 0L
        if (offline) {
            val startTime = params["clientTime"]?.toLongOrNull() ?: System.currentTimeMillis()
            val offlineDays = if (config.ignoreOfflineDays) {
                config.offlineDays
            } else {
                params["offlineDays"]?.toLongOrNull() ?: DEFAULT_OFFLINE_DAYS
            }
            validFrom = startTime
            validUntil = startTime + offlineDays * 24 * 60 * 60 * 1000L
        }

        val source = buildString {
            append(clientRandomness).append(';')
            append(serverRandomness).append(';')
            append(guid).append(';')
            append(offline)
            if (offline) {
                append(';').append(validFrom).append(';').append(validUntil)
            }
        }
        val signature = Base64.getEncoder().encodeToString(
            JrebelCrypto.signSha1(source.toByteArray(StandardCharsets.UTF_8)),
        )

        return obj(
            "serverVersion" to quote(SERVER_VERSION),
            "serverProtocolVersion" to quote(SERVER_PROTOCOL_VERSION),
            "serverGuid" to quote(SERVER_GUID),
            "groupType" to quote("managed"),
            "id" to "1",
            "licenseType" to "1",
            "evaluationLicense" to "false",
            "signature" to quote(signature),
            "serverRandomness" to quote(serverRandomness),
            "seatPoolType" to quote("standalone"),
            "statusCode" to quote("SUCCESS"),
            "offline" to offline.toString(),
            "validFrom" to validFrom.toString(),
            "validUntil" to validUntil.toString(),
            "company" to quote(username),
            "orderId" to quote(""),
            "zeroIds" to "[]",
            "licenseValidFrom" to LICENSE_VALID_FROM.toString(),
            "licenseValidUntil" to LICENSE_VALID_UNTIL.toString(),
        )
    }

    fun lease1(params: Map<String, String>): String {
        val username = params["username"].orEmpty()
        return obj(
            "serverVersion" to quote(SERVER_VERSION),
            "serverProtocolVersion" to quote(SERVER_PROTOCOL_VERSION),
            "serverGuid" to quote(SERVER_GUID),
            "groupType" to quote("managed"),
            "statusCode" to quote("SUCCESS"),
            "company" to quote(username),
            "msg" to "null",
            "statusMessage" to "null",
            "signature" to quote("dGVzdA=="),
        )
    }

    fun validateConnection(): String = obj(
        "serverVersion" to quote(SERVER_VERSION),
        "serverProtocolVersion" to quote(SERVER_PROTOCOL_VERSION),
        "serverGuid" to quote(SERVER_GUID),
        "groupType" to quote("managed"),
        "statusCode" to quote("SUCCESS"),
        "company" to quote("Administrator"),
        "canGetLease" to "true",
        "licenseType" to "1",
        "evaluationLicense" to "false",
        "seatPoolType" to quote("standalone"),
    )

    fun ping(salt: String): String {
        val xml = "<PingResponse><message></message><responseCode>OK</responseCode><salt>$salt</salt></PingResponse>"
        return withSignature(xml)
    }

    fun obtainTicket(salt: String, userName: String): String {
        val xml = "<ObtainTicketResponse><message></message><prolongationPeriod>607875500</prolongationPeriod>" +
            "<responseCode>OK</responseCode><salt>$salt</salt><ticketId>1</ticketId>" +
            "<ticketProperties>licensee=$userName\tlicenseType=0\t</ticketProperties></ObtainTicketResponse>"
        return withSignature(xml)
    }

    fun releaseTicket(salt: String): String {
        val xml = "<ReleaseTicketResponse><message></message><responseCode>OK</responseCode>" +
            "<salt>$salt</salt></ReleaseTicketResponse>"
        return withSignature(xml)
    }

    private fun withSignature(xml: String): String {
        val signature = JrebelCrypto.signMd5(xml.toByteArray(StandardCharsets.UTF_8))
        return "<!-- ${toHex(signature)} -->\n$xml"
    }

    private fun isOffline(params: Map<String, String>, config: ServerConfig): Boolean {
        if (params["product"] == "XRebel") {
            return false
        }
        return when (config.workMode) {
            LicenseServerSettings.WORK_MODE_OFFLINE -> true
            LicenseServerSettings.WORK_MODE_ONLINE -> false
            LicenseServerSettings.WORK_MODE_OLD_GUID -> !params["oldGuid"].isNullOrEmpty()
            else -> params["offline"]?.toBooleanStrictOrNull() ?: false
        }
    }

    private fun newServerRandomness(): String = buildString {
        repeat(11) { append(RANDOM_CHARSET[Random.nextInt(RANDOM_CHARSET.length)]) }
        append('=')
    }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            append(Character.forDigit((value shr 4) and 0xF, 16))
            append(Character.forDigit(value and 0xF, 16))
        }
    }

    private fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "${quote(key)}:$value"
        }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
