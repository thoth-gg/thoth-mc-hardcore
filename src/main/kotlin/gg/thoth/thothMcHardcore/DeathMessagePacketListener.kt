package gg.thoth.thothMcHardcore

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import java.util.concurrent.atomic.AtomicLong

class DeathMessagePacketListener(
    private val plugin: ThothMcHardcore,
    private val formatter: DeathMessageFormatter,
) {

    private val lastMessage = AtomicDeathMessageCache()

    private val adapter = object : PacketAdapter(
        plugin,
        ListenerPriority.NORMAL,
        PacketType.Play.Server.SYSTEM_CHAT,
    ) {
        override fun onPacketSending(event: PacketEvent) {
            val component = event.packet.chatComponents.readSafely(0) ?: return
            val subtitle = formatter.format(component.json) ?: return

            if (lastMessage.shouldSkip(subtitle)) {
                return
            }

            this@DeathMessagePacketListener.plugin.showGameOverTitle(subtitle)
        }
    }

    fun register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter)
    }

    fun close() {
        ProtocolLibrary.getProtocolManager().removePacketListener(adapter)
    }
}

private class AtomicDeathMessageCache {
    private val lastShownAt = AtomicLong(0)
    @Volatile
    private var lastSubtitle: String = ""

    fun shouldSkip(subtitle: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastShownAt.get()
        if (subtitle == lastSubtitle && now - previous < 1000) {
            return true
        }

        lastSubtitle = subtitle
        lastShownAt.set(now)
        return false
    }
}
