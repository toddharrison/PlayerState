package com.eharrison.canary.playerstate;

import java.util.HashMap;
import java.util.Map;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.position.Location;
import net.canarymod.commandsys.CommandDependencyException;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.PlayerRespawnedHook;
import net.canarymod.hook.player.PlayerRespawningHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;

import com.eharrison.canary.playerstate.hook.WorldEnterHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook.ExitCause;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	public static Logman logger;
	
	private final PlayerStateConfiguration config;
	private final IPlayerStateManager manager;
	private final PlayerStateCommand command;
	private final Map<String, WorldEnterHook> respawns;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.logger = getLogman();
		config = new PlayerStateConfiguration(this);
		manager = new PlayerStateManager();
		command = new PlayerStateCommand(manager);
		respawns = new HashMap<String, WorldEnterHook>();
		
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
		Canary.hooks().callHook(
				new WorldExitHook(player, player.getWorld(), player.getLocation(), ExitCause.DISCONNECT));
	}
	
	@HookHandler
	public void onRespawning(final PlayerRespawningHook hook) {
		final Player player = hook.getPlayer();
		Location respawnLoc = hook.getRespawnLocation();
		ExitCause cause = ExitCause.COMMAND;
		if (respawnLoc == null) {
			cause = ExitCause.DEATH;
		}
		if (cause == ExitCause.DEATH || respawnLoc.getWorld() != player.getWorld()) {
			final WorldExitHook exitHook = new WorldExitHook(player, player.getWorld(),
					player.getLocation(), respawnLoc, cause);
			Canary.hooks().callHook(exitHook);
			respawnLoc = exitHook.getToLocation();
			
			respawns.put(player.getUUIDString(),
					new WorldEnterHook(player, respawnLoc.getWorld(), player.getLocation(), respawnLoc));
			
			if (exitHook.getToLocation() != null) {
				hook.setRespawnLocation(exitHook.getToLocation());
			}
		}
	}
	
	@HookHandler
	public void onRespawn(final PlayerRespawnedHook hook) {
		final Player player = hook.getPlayer();
		final WorldEnterHook enterHook = respawns.remove(player.getUUIDString());
		if (enterHook != null) {
			enterHook.setToLocation(hook.getLocation());
			Canary.hooks().callHook(enterHook);
		}
	}
	
	@HookHandler
	public void onWorldEnter(final WorldEnterHook hook) throws DatabaseReadException {
		final Player player = hook.getPlayer();
		final String toWorld = hook.getWorld().getName();
		if (config.automateOnWorldChange()) {
			if (!manager.loadPlayerState(player, PlayerState.WORLD_PREFIX + toWorld)) {
				manager.clearPlayerState(player);
			}
		} else {
			if (PlayerState.registeredWorlds.contains(toWorld)) {
				if (!manager.loadPlayerState(player, PlayerState.WORLD_PREFIX + toWorld)) {
					manager.clearPlayerState(player);
				}
			} else {
				if (!manager.loadPlayerState(player, PlayerState.ALL_WORLDS)) {
					manager.clearPlayerState(player);
				}
			}
		}
	}
	
	@HookHandler
	public void onWorldExit(final WorldExitHook hook) throws DatabaseReadException,
			DatabaseWriteException {
		if (hook.getCause() != ExitCause.DEATH) {
			final Player player = hook.getPlayer();
			final String fromWorld = hook.getWorld().getName();
			if (config.automateOnWorldChange()) {
				manager.savePlayerState(player, PlayerState.WORLD_PREFIX + fromWorld);
			} else {
				if (PlayerState.registeredWorlds.contains(fromWorld)) {
					manager.savePlayerState(player, PlayerState.WORLD_PREFIX + fromWorld);
				} else {
					manager.savePlayerState(player, PlayerState.ALL_WORLDS);
				}
			}
		}
	}
}
