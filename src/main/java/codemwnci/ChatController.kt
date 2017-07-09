package codemwnci

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.*
import spark.Spark.*
import java.util.concurrent.atomic.AtomicLong

fun main(args: Array<String>) {
    port(9000)
    staticFileLocation("/public")
    webSocket("/chat", ChatWSHandler::class.java)
    init()
}

class User(val id: Long, val name: String)
class Message(val msgType: String, val data: Any)

@WebSocket
class ChatWSHandler {

    val users = HashMap<Session, User>()
    var uids = AtomicLong(0)

    @OnWebSocketConnect
    fun connected(session: Session) = println("session connected")

    @OnWebSocketMessage
    fun message(session: Session, message: String) {
        val json = ObjectMapper().readTree(message)
        // {type: "join/say", data: "name/msg"}
        when (json.get("type").asText()) {
            "join" -> {
                val user = User(uids.getAndIncrement(), json.get("data").asText())
                users.put(session, user)
                // tell this user about all other users
                emit(session, Message("users", users.values))
                // tell all other users, about this user
                broadcastToOthers(session, Message("join", user))
            }
            "say" -> {
                broadcast(Message("say", json.get("data").asText()))
            }
        }
        println("json msg ${message}")
    }


    @OnWebSocketClose
    fun disconnect(session: Session, code: Int, reason: String?) {
        // remove the user from our list
        val user = users.remove(session)
        // notify all other users this user has disconnected
        if (user != null) broadcast(Message("left", user))
    }


    fun emit(session: Session, message: Message) = session.remote.sendString(jacksonObjectMapper().writeValueAsString(message))
    fun broadcast(message: Message) = users.forEach() { emit(it.key, message) }
    fun broadcastToOthers(session: Session, message: Message) = users.filter { it.key != session }.forEach() { emit(it.key, message)}

}
