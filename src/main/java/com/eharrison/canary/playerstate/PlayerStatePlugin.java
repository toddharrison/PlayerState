package com.eharrison.canary.playerstate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.api.world.position.Vector3D;
import net.canarymod.chat.ChatFormat;
import net.canarymod.commandsys.CommandDependencyException;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.PlayerRespawnedHook;
import net.canarymod.hook.player.PlayerRespawningHook;
import net.canarymod.hook.player.TeleportHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;

import com.eharrison.canary.playerstate.PlayerState.Save;
import com.eharrison.canary.playerstate.hook.WorldChangeCause;
import com.eharrison.canary.playerstate.hook.WorldEnterHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook;
import com.eharrison.canary.util.JarUtil;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	private static final int MULTIPLAYER_SPAWN_RADIUS = 5;
	
	public static Logman LOG;
	
	private final PlayerStateConfiguration config;
	private final PlayerStateManager manager;
	private final PlayerStateCommand command;
	
	private final Map<String, WorldExitHook> movingPlayerMap;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.LOG = getLogman();
		movingPlayerMap = new HashMap<String, WorldExitHook>();
		
		try {
			JarUtil.exportResource(this, "PlayerState.cfg", new File("config/PlayerState"));
		} catch (final IOException e) {
			LOG.warn("Failed to create the default configuration file.", e);
		}
		
		config = new PlayerStateConfiguration(this);
		manager = new PlayerStateManager(this);
		command = new PlayerStateCommand(manager);
	}
	
	@Override
	public boolean enable() {
		boolean success = true;
		
		LOG.info("Enabling " + getName() + " Version " + getVersion());
		LOG.info("Authored by " + getAuthor());
		
		manager.start();
		
		Canary.hooks().registerListener(this, this);
		
		try {
			Canary.commands().registerCommands(command, this, false);
		} catch (final CommandDependencyException e) {
			LOG.error("Error registering commands: ", e);
			success = false;
		}
		
		return success;
	}
	
	@Override
	public void disable() {
		LOG.info("Disabling " + getName());
		
		Canary.commands().unregisterCommands(this);
		Canary.hooks().unregisterPluginListeners(this);
		manager.stop();
	}
	
	@HookHandler
	public void onConnection(final ConnectionHook hook) throws DatabaseReadException {
		final Player player = hook.getPlayer();
		final String state = getState(player.getWorld());
		
		if (hook.isFirstConnection()) {
			if (config.exactSpawn()) {
				final Location loc = Canary.getServer().getDefaultWorld().getSpawnLocation();
				preloadChunk(loc);
				player.setSpawnPosition(loc);
				player.setHome(loc);
				player.teleportTo(loc);
				player.message(ChatFormat.GOLD + "Entering state " + state);
			}
		}
		
		if (manager.loadPlayerState(player, state, getSaves(state))) {
			manager.restorePlayerLocation(player, state);
			player.message(ChatFormat.GOLD + "Loaded state " + state);
		}
	}
	
	@HookHandler
	public void onDisconnection(final DisconnectionHook hook) throws DatabaseWriteException {
		final Player player = hook.getPlayer();
		final String state = getState(player.getWorld());
		
		manager.savePlayerState(player, state, getSaves(state));
		LOG.info("Saved state " + state + " for " + player.getName());
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) {
		final Player player = hook.getPlayer();
		switch (hook.getTeleportReason()) {
			case RESPAWN:
				if (config.exactSpawn()) {
					if (!isBedRespawn(player)) {
						hook.setCanceled();
						final Location loc = hook.getDestination().getWorld().getSpawnLocation();
						preloadChunk(loc);
						player.teleportTo(loc);
					} else {
						preloadChunk(hook.getDestination());
					}
				} else {
					preloadChunk(hook.getDestination());
				}
				break;
			case COMMAND:
				if (config.exactSpawn()) {
					hook.setCanceled();
					final Location loc = hook.getDestination().getWorld().getSpawnLocation();
					preloadChunk(loc);
					player.teleportTo(loc);
				} else {
					preloadChunk(hook.getDestination());
				}
				break;
			default:
				// Ignore
				LOG.debug("Teleport cause: " + hook.getTeleportReason());
				preloadChunk(hook.getDestination());
		}
	}
	
	@HookHandler
	public void onRespawning(final PlayerRespawningHook hook) {
		final Player player = hook.getPlayer();
		final Location curLoc = player.getLocation();
		final Location spawnLoc = hook.getRespawnLocation();
		
		final WorldChangeCause reason;
		if (player.getHealth() == 0) {
			reason = WorldChangeCause.DEATH;
		} else {
			reason = WorldChangeCause.COMMAND;
		}
		
		if (spawnLoc != null && curLoc.getWorld() != spawnLoc.getWorld()) {
			final WorldExitHook worldExitHook = new WorldExitHook(player, curLoc.getWorld(), curLoc,
					spawnLoc, reason);
			Canary.hooks().callHook(worldExitHook);
			movingPlayerMap.put(player.getUUIDString(), worldExitHook);
		}
	}
	
	@HookHandler
	public void onRespawned(final PlayerRespawnedHook hook) {
		final Player player = hook.getPlayer();
		
		final WorldExitHook worldExitHook = movingPlayerMap.remove(player.getUUIDString());
		if (worldExitHook != null) {
			final Location spawnLoc = hook.getLocation();
			Canary.hooks().callHook(
					new WorldEnterHook(player, spawnLoc.getWorld(), worldExitHook.getFromLocation(),
							spawnLoc, worldExitHook.getReason()));
		}
	}
	
	@HookHandler
	public void onWorldEnter(final WorldEnterHook hook) throws DatabaseReadException {
		final Player player = hook.getPlayer();
		final String fromState = getState(hook.getFromLocation().getWorld());
		final String toState = getState(hook.getToLocation().getWorld());
		
		if (!toState.equals(fromState)) {
			if (manager.loadPlayerState(player, toState, getSaves(toState))) {
				player.message(ChatFormat.GOLD + "Loaded state " + toState);
			} else {
				player.message(ChatFormat.GOLD + "Entering state " + toState);
			}
		}
	}
	
	@HookHandler
	public void onWorldExit(final WorldExitHook hook) throws DatabaseWriteException {
		final Player player = hook.getPlayer();
		final String fromState = getState(hook.getFromLocation().getWorld());
		final String toState = getState(hook.getToLocation().getWorld());
		
		if (hook.getReason() == WorldChangeCause.DEATH) {
			// TODO
			player.message(ChatFormat.GOLD + "Applying death penalty to state " + fromState);
		}
		
		if (!toState.equals(fromState)) {
			manager.savePlayerState(player, fromState, getSaves(fromState));
			player.message(ChatFormat.GOLD + "Saved state " + fromState);
		}
	}
	
	private boolean isBedRespawn(final Player player) {
		final Vector3D spawn = new Vector3D(player.getSpawnPosition());
		final Vector3D worldSpawn = new Vector3D(player.getSpawnPosition().getWorld()
				.getSpawnLocation());
		spawn.setY(0);
		worldSpawn.setY(0);
		if (spawn.getDistance(worldSpawn) > MULTIPLAYER_SPAWN_RADIUS) {
			return true;
		}
		return false;
	}
	
	private String getState(final World world) {
		String state = PlayerState.managedWorldStates.get(world.getName());
		if (state == null) {
			state = config.getState(world);
		}
		return state;
	}
	
	private Save[] getSaves(final String state) {
		Save[] saves = PlayerState.managedStateSaves.get(state);
		if (saves == null) {
			saves = config.getSaves(state);
		}
		return saves;
	}
	
	private void preloadChunk(final Location loc) {
		final World world = loc.getWorld();
		if (!world.isChunkLoaded(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
			loc.getWorld().loadChunk(loc);
		}
	}
}
