package com.eharrison.canary.playerstate;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.commandsys.CommandDependencyException;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.TeleportHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	public static Logman logger;
	
	private final PlayerStateConfiguration config;
	private final PlayerStateManager manager;
	private final PlayerStateCommand command;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.logger = getLogman();
		config = new PlayerStateConfiguration(this);
		manager = new PlayerStateManager();
		command = new PlayerStateCommand(manager);
	}
	
	@Override
	public boolean enable() {
		boolean success = true;
		
		logger.info("Enabling " + getName() + " Version " + getVersion());
		logger.info("Authored by " + getAuthor());
		
		Canary.hooks().registerListener(this, this);
		
		try {
			Canary.commands().registerCommands(command, this, false);
		} catch (final CommandDependencyException e) {
			logger.error("Error registering commands: ", e);
			success = false;
		}
		
		return success;
	}
	
	@Override
	public void disable() {
		logger.info("Disabling " + getName());
		Canary.commands().unregisterCommands(this);
		Canary.hooks().unregisterPluginListeners(this);
	}
	
	@HookHandler
	public void onConnection(final ConnectionHook hook) throws DatabaseReadException {
		if (config.automateOnWorldChange()) {
			final Player player = hook.getPlayer();
			manager.changePlayerState(player, player.getWorld().getName());
		}
	}
	
	@HookHandler
	public void onDisconnection(final DisconnectionHook hook) throws DatabaseWriteException {
		if (config.automateOnWorldChange()) {
			final Player player = hook.getPlayer();
			manager.savePlayerState(player, player.getWorld().getName());
		}
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) throws DatabaseReadException {
		if (config.automateOnWorldChange()) {
			if (hook.getDestination().getWorld() != hook.getCurrentLocation().getWorld()) {
				manager.changePlayerState(hook.getPlayer(), hook.getDestination().getWorldName());
			}
		}
	}
}
