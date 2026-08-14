package io.legado.app.web

import fi.iki.elonen.NanoWSD
import io.legado.app.help.config.AppConfig
import io.legado.app.service.WebService
import io.legado.app.web.socket.*

class WebSocketServer(port: Int) : NanoWSD(port) {

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        WebService.serve()
        if (!checkToken(handshake)) return null
        return when (handshake.uri) {
            "/bookSourceDebug" -> {
                BookSourceDebugWebSocket(handshake)
            }
            "/rssSourceDebug" -> {
                RssSourceDebugWebSocket(handshake)
            }
            "/searchBook" -> {
                BookSearchWebSocket(handshake)
            }
            else -> null
        }
    }

    private fun checkToken(handshake: IHTTPSession): Boolean {
        val token = AppConfig.webToken
        if (token.isBlank()) return true
        val auth = handshake.headers["authorization"]
        if (auth == "Bearer $token") return true
        return handshake.parms["token"] == token
    }
}
