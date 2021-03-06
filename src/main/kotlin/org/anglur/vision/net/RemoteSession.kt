/*
 *     Vision - free remote desktop software built with Kotlin
 *     Copyright (C) 2016  Jonathan Beaudoin
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.anglur.vision.net

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.DatagramPacket
import org.anglur.vision.guid.UID
import org.anglur.vision.net.packet.Packet
import org.anglur.vision.util.extensions.rand
import org.anglur.vision.util.extensions.writePacket
import org.anglur.vision.view.DesktopFrame
import org.anglur.vision.view.VisionGUI
import kotlin.concurrent.thread

class RemoteSession(val id: String, val password: String) {
	
	val partner = UID.address(id)
	
	val secret = rand(Long.MIN_VALUE..Long.MAX_VALUE)
	
	lateinit var ctx: ChannelHandlerContext
	
	lateinit var desktopFrame: DesktopFrame
	
	var connected = false
	
	val write = Unpooled.buffer()!!
	
	init {
		Sessions += this
	}
	
	fun connect() {
		if (connected) return
		
		udp {
			println("Rite")
			connected = true
			
			desktopFrame = DesktopFrame.show()
			
			thread {
				var iterations: Int = 0
				var time: Long = 0
				while (desktopFrame.isShowing) {
					val stamp = System.currentTimeMillis()
					desktopFrame.display(VisionGUI.captureMode.capture())
					time += System.currentTimeMillis() - stamp
					
					if (iterations++ % 100 == 0) {
						println("Took " + time / iterations.toDouble() + "ms avg per frame (over 100 frames)")
					}
				}
			}
		}.bind(43594).await()
	}
	
	fun write(packet: Packet) {
		write.writePacket(packet, this)
		ctx.channel().writeAndFlush(DatagramPacket(write, partner))
	}
	
	fun disconnect() {
		connected = false
		println("disconnected session")
		desktopFrame.closeModal()
	}
	
}

object Sessions {
	
	private val sessions = mutableMapOf<Long, RemoteSession>()
	private val secrets = mutableMapOf<String, Long>()
	
	operator fun get(secret: Long) = sessions[secret]
	
	operator fun get(id: String) = sessions[secrets[id]]!!
	
	infix operator fun plusAssign(session: RemoteSession) {
		require(!sessions.containsKey(session.secret))
		
		sessions.put(session.secret, session)
		secrets.put(session.id, session.secret)
	}
	
	infix operator fun minusAssign(session: RemoteSession) {
		require(sessions.containsKey(session.secret))
		
		sessions.remove(session.secret)
		secrets.remove(session.id)
	}
	
}