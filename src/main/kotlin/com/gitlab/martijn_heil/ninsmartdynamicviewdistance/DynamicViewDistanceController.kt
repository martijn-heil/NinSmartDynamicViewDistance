/*
 * NinSmartDynamicViewDistance
 * Copyright (C) 2022  Martijn Heil
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.gitlab.martijn_heil.ninsmartdynamicviewdistance

import org.bukkit.ChatColor.*
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.MONITOR
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.Plugin
import java.time.Duration
import kotlin.math.max
import kotlin.math.min

data class LastKnownPosition(val location: Location, val timestamp: Int)

private val CHAT_PREFIX = "$DARK_RED[ViewDistance] $GRAY"

class DynamicViewDistanceController(
	private val server: Server,
	private val plugin: Plugin,
	desiredViewDistance: Int
) {
	private var isDestroyed = false

	val minimumViewDistance = 2
	private val decreaseStepSize = 4
	private val increaseStepSize = 4

	var desiredViewDistance = desiredViewDistance
		set(value) {
			check(value >= minimumViewDistance)
			val oldValue = field
			field = value
			val msg = "Desired view distance has been changed. $oldValue -> $value"
			plugin.logger.info(msg)
			broadcastToAdmins(msg)
			if (currentViewDistance > value) currentViewDistance = value
		}

	var currentViewDistance = desiredViewDistance
		private set(value) {
			val oldValue = field
			field = value
			val msg = "Global view distance has changed. $oldValue -> $value"
			plugin.logger.info(msg)
			broadcastToAdmins(msg)
		}

	private val fixedViewDistances = HashMap<Player, Int>()
	private val lastKnownPositions = HashMap<Player, LastKnownPosition>()
	private val tasks = ArrayList<Int>()

	private val listener = object : Listener {
		@EventHandler(priority = MONITOR)
		fun onPlayerTeleport(e: PlayerTeleportEvent) {
			updateViewDistanceFor(e.player)
		}

		@EventHandler(priority = MONITOR)
		fun onPlayerJoin(e: PlayerJoinEvent) {
			server.scheduler.scheduleSyncDelayedTask(plugin, {
				if (!isDestroyed) updateViewDistanceFor(e.player)
			}, 0)
		}

		@EventHandler(priority = MONITOR)
		fun onPlayerOpenInventory(e: InventoryOpenEvent) {
			if (e.player is Player) {
				updateViewDistanceFor(e.player as Player)
			}
		}
	}

	fun heightenGracefully() {
		setViewDistanceGracefully(min(desiredViewDistance, currentViewDistance + increaseStepSize))
	}

	fun lowerGracefully() {
		setViewDistanceGracefully(max(minimumViewDistance, currentViewDistance - decreaseStepSize))
	}

	fun lowerNow() {
		setViewDistanceNow(max(minimumViewDistance, currentViewDistance - decreaseStepSize))
	}

	fun setViewDistanceNow(target: Int) {
		if (target == currentViewDistance) return
		val msg = "Immediately setting view distance to $target!"
		plugin.logger.warning(msg)
		broadcastToAdmins("$RED$msg")
		currentViewDistance = target
		server.onlinePlayers.forEach { updateViewDistanceFor(it) }
	}

	fun setViewDistanceGracefully(target: Int) {
		if (target == currentViewDistance) return
		val msg = "Gracefully setting view distance to $target"
		plugin.logger.info(msg)
		broadcastToAdmins(msg)
		currentViewDistance = target
	}

	fun updateViewDistanceFor(p: Player) {
		if (!fixedViewDistances.containsKey(p) && p.viewDistance != currentViewDistance) {
			plugin.logger.info("Updating view distance for ${p.name} to $currentViewDistance")
			setViewDistanceMuffled(p, currentViewDistance)
		}
	}

	fun setFixedViewDistance(p: Player, value: Int) {
		fixedViewDistances[p] = value
		setViewDistanceMuffled(p, value)
	}

	fun removeFixedViewDistance(p: Player) {
		fixedViewDistances.remove(p)
		setViewDistanceMuffled(p, currentViewDistance)
	}

	fun start() {
		tasks.add(server.scheduler.scheduleSyncRepeatingTask(plugin, { tick() }, 0, 1))

		tasks.add(server.scheduler.scheduleSyncRepeatingTask(plugin, {
			server.onlinePlayers.forEach {
				var lastKnownPosition = lastKnownPositions[it]

				if (lastKnownPosition == null || it.location.distance(lastKnownPosition.location) > 0.5) {
					lastKnownPosition = LastKnownPosition(it.location, server.currentTick)
					lastKnownPositions[it] = lastKnownPosition
				}

				if (server.currentTick - lastKnownPosition.timestamp >= 200) { // 5 seconds
					updateViewDistanceFor(it)
				}
			}
		}, 0, 10))

		server.pluginManager.registerEvents(listener, plugin)
	}

	private var lastTickTime: ULong = 0U
	private val UPDATE_INTERVAL: ULong = 1000UL * 60UL * 5UL // Update interval in milliseconds: 5 minutes
	private fun tick() {
		val tps = server.tps
		val fiveMinuteAverageTps = tps[1]

		val now = System.currentTimeMillis().toULong()

		if (now - lastTickTime < UPDATE_INTERVAL) return

		when {
			fiveMinuteAverageTps < 15 -> {
				val msg = "Five minute average TPS is below 15! " +
						"Entering emergency view distance mode! Reducing view distance to $minimumViewDistance!"
				plugin.logger.severe(msg)
				broadcastToAdmins(msg)
				setViewDistanceNow(minimumViewDistance)
			}
			fiveMinuteAverageTps < 17 -> {
				val msg = "Five minute average TPS is below 17, lowering view distance immediately!"
				plugin.logger.warning(msg)
				broadcastToAdmins(msg)
				lowerNow()
			}
			fiveMinuteAverageTps < 19 -> {
				val msg = "Five minute average TPS is below 19, lowering view distance gracefully."
				plugin.logger.info(msg)
				broadcastToAdmins(msg)
				lowerGracefully()
			}
			fiveMinuteAverageTps >= 19.9 -> {
				val msg = "Five minute average TPS is >= 19.9, " +
						"increasing view distance gracefully if so desired."
				plugin.logger.info(msg)
				heightenGracefully()
			}
			else -> plugin.logger.info("Nothing to do, see you in five minutes!")
		}

		lastTickTime = now
	}

	val timeUntilNextUpdate
		get() = Duration.ofMillis(max(0,(lastTickTime + UPDATE_INTERVAL).toLong() - System.currentTimeMillis()))

	fun destroy() {
		tasks.forEach { server.scheduler.cancelTask(it) }
		isDestroyed = true
	}

	private fun setViewDistanceMuffled(p: Player, distance: Int) {
		try {
			p.viewDistance = distance
		} catch(ex: IllegalStateException) {
			if (ex.message == "Player is not attached to world") {
				plugin.logger.warning("Could not set view distance for ${p.name}: Player is not attached to world.")
			} else {
				throw ex
			}
		}
	}

	private fun broadcastToAdmins(msg: String) {
		server.broadcast("$CHAT_PREFIX $msg", "ninsmartdynamicviewdistance.broadcast")
	}
}
