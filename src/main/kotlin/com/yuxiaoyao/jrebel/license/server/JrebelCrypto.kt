package com.yuxiaoyao.jrebel.license.server

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * JRebel 协议签名。密钥与 Go 版保持一致：
 * - SHA1withRSA 用于 leases 接口
 * - MD5withRSA 用于旧版 Atlassian RPC 接口
 */
object JrebelCrypto {

    private val jrebelKey: PrivateKey by lazy { loadKey("/keys/jrebel-private.pem") }
    private val asmKey: PrivateKey by lazy { loadKey("/keys/asm-private.pem") }

    fun signSha1(data: ByteArray): ByteArray = sign("SHA1withRSA", jrebelKey, data)

    fun signMd5(data: ByteArray): ByteArray = sign("MD5withRSA", asmKey, data)

    private fun sign(algorithm: String, key: PrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance(algorithm)
        signature.initSign(key)
        signature.update(data)
        return signature.sign()
    }

    private fun loadKey(resource: String): PrivateKey {
        val pem = JrebelCrypto::class.java.getResourceAsStream(resource)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing key resource: $resource")

        val base64 = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")

        val spec = PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64))
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}
