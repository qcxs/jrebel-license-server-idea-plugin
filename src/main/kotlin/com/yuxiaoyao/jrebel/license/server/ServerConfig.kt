package com.yuxiaoyao.jrebel.license.server

data class ServerConfig(
    val port: Int,
    val uuid: String,
    val offlineDays: Long,
    val ignoreOfflineDays: Boolean,
    val workMode: Int,
    val exportSchema: String,
    val exportHost: String,
)
