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

import com.gitlab.martijn_heil.nincommands.common.CommandTarget
import com.gitlab.martijn_heil.nincommands.common.Sender
import com.sk89q.intake.Command
import com.sk89q.intake.CommandException
import com.sk89q.intake.Require
import org.bukkit.ChatColor.GRAY
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val controller: DynamicViewDistanceController
	get() = NinSmartDynamicViewDistance.instance.controller

private fun checkViewDistanceRaw(distance: Int) {
	if (distance < 2 || distance > 32)
		throw CommandException("Please enter a distance between 2 and 32 chunks.")
}

private fun checkViewDistance(distance: Int) {
	val minimum = controller.minimumViewDistance
	val maximum = 32
	if (distance < minimum || distance > maximum)
		throw CommandException("Please enter a distance between $minimum and $maximum chunks.")
}

class Commands(private val server: Server) {
	@Command(aliases = ["status"], desc = "Show current status")
	@Require("ninsmartdynamicviewdistance.command.status")
	fun status(@Sender sender: CommandSender) {
		val playerCount = server.onlinePlayers.size
		val playersWithGlobalViewDistance = server.onlinePlayers
			.filter { it.viewDistance == controller.currentViewDistance }.size

		sender.sendMessage(GRAY.toString() + "Players with too low client-side render distances:",)
		server.onlinePlayers
			.filter { it.clientViewDistance < controller.currentViewDistance }
			.groupBy { it.clientViewDistance }
			.toSortedMap()
			.map { entry -> "  ${GRAY}${entry.key}: ${entry.value.map { it.name }}" }
			.forEach { sender.sendMessage(it) }

		val timeUntilNextUpdate = controller.timeUntilNextUpdate
		sender.sendMessage(
				GRAY.toString() + "Desired view distance: ${controller.desiredViewDistance}",
				GRAY.toString() + "Current view distance: ${controller.currentViewDistance}",
				GRAY.toString() + "Minimum view distance: ${controller.minimumViewDistance}",
				GRAY.toString() + "Players with current global view distance as view distance: $playersWithGlobalViewDistance/$playerCount",
				GRAY.toString() + "Time until next update: ${timeUntilNextUpdate.toMinutes()}m ${timeUntilNextUpdate.toSecondsPart()}s")
	}

	class PlayerCommand {
		@Command(aliases = ["get"], desc = "Get a player's server-side view distance.")
		@Require("ninsmartdynamicviewdistance.command.player.get")
		fun get(@Sender sender: CommandSender, @CommandTarget target: Player) {
			sender.sendMessage(target.viewDistance.toString())
		}

		@Command(aliases = ["getclient"], desc = "Get a player's client-side view distance.")
		@Require("ninsmartdynamicviewdistance.command.player.getclient")
		fun getclient(@Sender sender: CommandSender, @CommandTarget target: Player) {
			sender.sendMessage(target.clientViewDistance.toString())
		}

		@Command(aliases = ["set"], desc = "Set a player's server-side view distance.")
		@Require("ninsmartdynamicviewdistance.command.player.set")
		fun set(distance: Int, @CommandTarget("ninsmartdynamicviewdistance.command.player.set.others") target: Player) {
			checkViewDistanceRaw(distance)
			controller.setFixedViewDistance(target, distance)
		}

		@Command(aliases = ["reset"], desc = "Reset a player's server-side view distance.")
		@Require("ninsmartdynamicviewdistance.command.player.reset")
		fun reset(@CommandTarget("ninsmartdynamicviewdistance.command.player.reset.others") target: Player) {
			controller.removeFixedViewDistance(target)
		}
	}

	class Global {
		@Command(aliases = ["get"], desc = "Get the current global view distance.")
		@Require("ninsmartdynamicviewdistance.command.global.get")
		fun get(@Sender sender: CommandSender) {
			sender.sendMessage(controller.currentViewDistance.toString())
		}

		@Command(aliases = ["set"], desc = "Set the current global view distance.")
		@Require("ninsmartdynamicviewdistance.command.global.set")
		fun set(@Sender sender: CommandSender, distance: Int) {
			checkViewDistance(distance)
			controller.setViewDistanceGracefully(distance)
		}

		@Command(aliases = ["setnow"], desc = "Set the current global view distance.")
		@Require("ninsmartdynamicviewdistance.command.global.setnow")
		fun setnow(@Sender sender: CommandSender, distance: Int) {
			checkViewDistance(distance)
			controller.setViewDistanceNow(distance)
		}

		class Desired {
			@Command(aliases = ["set"], desc = "Set the currently desired global view distance.")
			@Require("ninsmartdynamicviewdistance.command.global.desired.set")
			fun set(@Sender sender: CommandSender, distance: Int) {
				checkViewDistance(distance)
				controller.desiredViewDistance = distance
			}

			@Command(aliases = ["get"], desc = "Get the currently desired global view distance.")
			@Require("ninsmartdynamicviewdistance.command.global.desired.get")
			fun get(@Sender sender: CommandSender) {
				sender.sendMessage(controller.desiredViewDistance.toString())
			}
		}
	}
}
