package com.eharrison.canary.playerstate;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.position.Location;
import net.canarymod.commandsys.CommandDependencyException;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.PlayerRespawningHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;

import com.eharrison.canary.playerstate.hook.WorldEnterHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	public static Logman logger;
	
	private final PlayerStateConfiguration config;
	private final IPlayerStateManager manager;
	private final PlayerStateCommand command;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.logger = getLogman();
		config = new PlayerStateConfiguration(this);
		manager = new PlayerStateManager();
		command = new PlayerStateCommand(manager);
		
		PlayerState.playerStateManager = manager;
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
	public void onConnection(final ConnectionHook hook) {
		final Player player = hook.getPlayer();
		Canary.hooks().callHook(new WorldEnterHook(player, player.getWorld()));
	}
	
	@HookHandler
	public void onDisconnection(final DisconnectionHook hook) {
		final Player player = hook.getPlayer();
		Canary.hooks().callHook(new WorldExitHook(player, player.getWorld(), player.getLocation()));
	}
	
	@HookHandler
	public void onRespawning(final PlayerRespawningHook hook) {
		final Player player = hook.getPlayer();
		Location respawnLoc = hook.getRespawnLocation();
		if (respawnLoc != null && respawnLoc.getWorld() != player.getWorld()) {
			final WorldExitHook exitHook = new WorldExitHook(player, player.getWorld(),
					player.getLocation(), respawnLoc);
			Canary.hooks().callHook(exitHook);
			respawnLoc = exitHook.getToLocation();
			
			final WorldEnterHook enterHook = new WorldEnterHook(player, respawnLoc.getWorld(),
					player.getLocation(), respawnLoc);
			Canary.hooks().callHook(enterHook);
			
			if (enterHook.getToLocation() != null) {
				hook.setRespawnLocation(enterHook.getToLocation());
			}
		}
	}
	
	@HookHandler
	public void onWorldEnter(final WorldEnterHook hook) throws DatabaseReadException {
		if (config.automateOnWorldChange()) {
			final Player player = hook.getPlayer();
			if (!manager.loadPlayerState(player, "WORLD-" + hook.getWorld().getName())) {
				manager.clearPlayerState(player);
			}
		}
	}
	
	@HookHandler
	public void onWorldExit(final WorldExitHook hook) throws DatabaseWriteException {
		if (config.automateOnWorldChange()) {
			manager.savePlayerState(hook.getPlayer(), "WORLD-" + hook.getWorld().getName());
		}
	}
}
