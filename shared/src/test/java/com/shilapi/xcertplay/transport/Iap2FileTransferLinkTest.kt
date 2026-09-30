package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test

class Iap2FileTransferLinkTest {
    @Test fun fileTransfersAndControlMessagesUseSeparateSessionsThroughNegotiatedLinks() {
        val sender = Iap2LinkEngine()
        val receiver = Iap2LinkEngine()
        sender.start(true, 0); receiver.start(false, 0)
        fun exchange() {
            repeat(12) {
                sender.takeOutput().takeIf { it.isNotEmpty() }?.let { receiver.feed(it, 10) }
                receiver.takeOutput().takeIf { it.isNotEmpty() }?.let { sender.feed(it, 10) }
            }
        }
        exchange()
        assertEquals(Iap2LinkEngine.State.NORMAL, sender.state())
        assertEquals(Iap2LinkEngine.State.NORMAL, receiver.state())
        while (receiver.pollEvent() != null) { }
        val cover = byteArrayOf(7, 4)
        sender.sendFileTransfer(cover, 20)
        sender.sendControl(byteArrayOf(1, 2, 3), 20)
        exchange()
        val received = mutableListOf<Iap2LinkEngine.Event>()
        while (true) received += receiver.pollEvent() ?: break
        assertArrayEquals(cover, (received.single { it is Iap2LinkEngine.Event.FileTransfer } as Iap2LinkEngine.Event.FileTransfer).bytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), (received.single { it is Iap2LinkEngine.Event.Control } as Iap2LinkEngine.Event.Control).bytes)
        receiver.sendFileTransfer(byteArrayOf(7, 1), 30)
        exchange()
        var reply: ByteArray? = null
        while (true) { val event = sender.pollEvent() ?: break; if (event is Iap2LinkEngine.Event.FileTransfer) reply = event.bytes }
        assertArrayEquals(byteArrayOf(7, 1), reply)
    }
}
