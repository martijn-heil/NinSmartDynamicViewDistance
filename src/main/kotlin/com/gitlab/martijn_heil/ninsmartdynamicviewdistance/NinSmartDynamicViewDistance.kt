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

import com.gitlab.martijn_heil.nincommands.common.CommonModule
import com.gitlab.martijn_heil.nincommands.common.bukkit.BukkitAuthorizer
import com.gitlab.martijn_heil.nincommands.common.bukkit.provider.BukkitModule
import com.gitlab.martijn_heil.nincommands.common.bukkit.provider.sender.BukkitSenderModule
import com.gitlab.martijn_heil.nincommands.common.bukkit.registerCommand
import com.gitlab.martijn_heil.nincommands.common.bukkit.unregisterCommand
import com.sk89q.intake.Intake
import com.sk89q.intake.fluent.CommandGraph
import com.sk89q.intake.parametric.ParametricBuilder
import com.sk89q.intake.parametric.provider.PrimitivesModule
import org.bukkit.command.Command
import org.bukkit.plugin.java.JavaPlugin

class NinSmartDynamicViewDistance : JavaPlugin() {
	lateinit var controller: DynamicViewDistanceController
	private lateinit var commands: Collection<Command>

    override fun onEnable() {
		controller = DynamicViewDistanceController(server, this, 32)
        controller.start()
		instance = this

        val injector = Intake.createInjector()
        injector.install(PrimitivesModule())
        injector.install(BukkitModule(server))
        injector.install(BukkitSenderModule())
        injector.install(CommonModule())

        val builder = ParametricBuilder(injector)
        builder.authorizer = BukkitAuthorizer()

        val rootDispatcherNode = CommandGraph().builder(builder).commands()
        val viewDistanceCommand = rootDispatcherNode.group("viewdistance", "vd")
        viewDistanceCommand.registerMethods(Commands(server))
        viewDistanceCommand.group("global").registerMethods(Commands.Global())
            .group("desired").registerMethods(Commands.Global.Desired())
        viewDistanceCommand.group("player").registerMethods(Commands.PlayerCommand())
        val dispatcher = rootDispatcherNode.dispatcher

		commands = dispatcher.commands.mapNotNull { registerCommand(it.callable, this, it.allAliases.toList()) }
    }

    override fun onDisable() {
        controller.destroy()
		commands.forEach { unregisterCommand(it) }
    }

	companion object {
		lateinit var instance: NinSmartDynamicViewDistance
	}
}
