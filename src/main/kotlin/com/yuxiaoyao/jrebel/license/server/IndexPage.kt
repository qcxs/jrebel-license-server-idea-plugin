package com.yuxiaoyao.jrebel.license.server

import java.nio.charset.StandardCharsets

object IndexPage {

    private val template: String by lazy {
        IndexPage::class.java.getResourceAsStream("/web/index.html")
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Missing resource: /web/index.html")
    }

    fun render(host: String, uuid: String): String = template
        .replace("{{HOST}}", escapeHtml(host))
        .replace("{{UUID}}", escapeHtml(uuid))

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
