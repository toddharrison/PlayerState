package com.eharrison.canary.playerstate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.chat.ChatFormat;
import net.canarymod.commandsys.CommandDependencyException;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.command.PlayerCommandHook;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.PlayerDeathHook;
import net.canarymod.hook.player.TeleportHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;
import net.visualillusionsent.utils.TaskManager;

import com.eharrison.canary.playerstate.PlayerState.Save;
import com.eharrison.canary.playerstate.hook.WorldDeathHook;
import com.eharrison.canary.playerstate.hook.WorldEnterHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook;
import com.eharrison.canary.util.JarUtil;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	private static final int MULTIPLAYER_SPAWN_RADIUS = 5;
	private static final long TELEPORT_DELAY_SECONDS = 3;
	private static final int TELEPORT_DISTANCE_FUDGE = 1;
	
	public static Logman LOG;
	
	private final PlayerStateConfiguration config;
	private final PlayerStateManager manager;
	private final PlayerStateCommand command;
	
	private final Collection<String> connectingPlayers;
	private final Collection<String> tpingPlayerList;
	private final Collection<String> readyPlayers;
	private final Map<String, Location> deadPlayers;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.LOG = getLogman();
		
		// connectingPlayers = new ArrayList<String>();
		// tpingPlayerList = new ArrayList<String>();
		// readyPlayers = new ArrayList<String>();
		// deadPlayers = new HashMap<String, Location>();
		connectingPlayers = Collections.synchronizedCollection(new ArrayList<String>());
		tpingPlayerList = Collections.synchronizedCollection(new ArrayList<String>());
		readyPlayers = Collections.synchronizedCollection(new ArrayList<String>());
		deadPlayers = Collections.synchronizedMap(new HashMap<String, Location>());
		
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
		final String uuid = player.getUUIDString();
		
		connectingPlayers.add(uuid);
		
		if (hook.isFirstConnection()) {
			final Location loc = Canary.getServer().getDefaultWorld().getSpawnLocation();
			
			player.setSpawnPosition(loc);
			player.setHome(loc);
			
			preloadChunk(loc);
			readyPlayers.add(uuid);
			if (config.exactSpawn()) {
				player.teleportTo(loc);
			}
			
			player.message(ChatFormat.GOLD + "Entering state " + state);
		} else if (manager.loadPlayerState(player, state, getSaves(state))) {
			final Location loc = manager.getPlayerReturnLocation(player, state);
			preloadChunk(loc);
			readyPlayers.add(uuid);
			player.teleportTo(loc);
			player.message(ChatFormat.GOLD + "Loaded state " + state);
		}
	}
	
	@HookHandler
	public void onDisconnection(final DisconnectionHook hook) throws DatabaseWriteException {
		final Player player = hook.getPlayer();
		final String state = getState(player.getWorld());
		manager.savePlayerState(player, state, getSaves(state));
	}
	
	@HookHandler
	public void onCommand(final PlayerCommandHook hook) throws InterruptedException,
			ExecutionException {
		final String[] command = hook.getCommand();
		if (command[0].equals("/spawn")) {
			hook.setCanceled();
			
			final Player player = hook.getPlayer();
			World world = player.getWorld();
			if (command.length > 1) {
				world = Canary.getServer().getWorld(command[1]);
			}
			final Location spawn = world.getSpawnLocation();
			delayedTeleport(player, spawn, ChatFormat.YELLOW + "Teleported to spawn");
		} else if (command[0].equals("/home")) {
			hook.setCanceled();
			
			final Player player = hook.getPlayer();
			Player targetPlayer = player;
			if (command.length > 1) {
				targetPlayer = Canary.getServer().getPlayer(command[1]);
			}
			final Location loc = targetPlayer.getHome();
			delayedTeleport(player, loc, ChatFormat.RED + "Going home");
		} else if (command[0].equals("/tp")) {
			// TODO: Don't display the message until after success?
			// foo(player, loc, "Teleported " + targetPlayer.getName() + " to " + loc);
		}
	}
	
	@HookHandler
	public void onDeath(final PlayerDeathHook hook) {
		final Player player = hook.getPlayer();
		deadPlayers.put(player.getUUIDString(), player.getLocation());
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) {
		final Player player = hook.getPlayer();
		
		switch (hook.getTeleportReason()) {
			case COMMAND:
			case RESPAWN:
			case PLUGIN:
				final String uuid = player.getUUIDString();
				tpingPlayerList.add(uuid);
				if (readyPlayers.contains(uuid)) {
					readyPlayers.remove(uuid);
					tpingPlayerList.remove(uuid);
				} else if (connectingPlayers.contains(uuid)) {
					connectingPlayers.remove(uuid);
				} else if (deadPlayers.containsKey(uuid)) {
					hook.setCanceled();
					final Location diedLoc = deadPlayers.remove(uuid);
					final Location spawnLoc = player.getSpawnPosition();
					final WorldDeathHook worldDeathHook = new WorldDeathHook(player, diedLoc, spawnLoc);
					worldDeathHook.call();
					readyPlayers.add(uuid);
					player.teleportTo(worldDeathHook.getSpawnLocation());
				} else {
					hook.setCanceled();
					delayedTeleport(player, hook.getDestination(), null);
				}
				break;
			default:
				// Do nothing
				// LOG.info(hook.getTeleportReason());
		}
	}
	
	private void delayedTeleport(final Player player, final Location destination,
			final String successMessage) {
		player.message(ChatFormat.GOLD + "Stand still for " + TELEPORT_DELAY_SECONDS
				+ " seconds to teleport...");
		
		final String uuid = player.getUUIDString();
		final Location curLoc = player.getLocation();
		preloadChunk(destination);
		
		TaskManager.scheduleDelayedTask(new Runnable() {
			@Override
			public void run() {
				if (curLoc.getDistance(player.getLocation()) > TELEPORT_DISTANCE_FUDGE) {
					player.message(ChatFormat.RED + "Teleportation cancelled");
					tpingPlayerList.remove(uuid);
				} else {
					final Location targetLoc;
					final WorldExitHook worldExitHook;
					if (curLoc.getWorld() != destination.getWorld()) {
						worldExitHook = new WorldExitHook(player, curLoc.getWorld(), curLoc, destination);
						targetLoc = worldExitHook.getToLocation();
					} else {
						worldExitHook = null;
						targetLoc = destination;
					}
					
					preloadChunk(targetLoc);
					LOG.info(targetLoc.getWorld().getBlockAt(targetLoc));
					readyPlayers.add(uuid);
					player.teleportTo(targetLoc);
					
					// // TODO Wait until it's safe to spawn in again
					// TaskManager.scheduleDelayedTask(new Runnable() {
					// @Override
					// public void run() {
					// readyPlayers.add(uuid);
					// player.teleportTo(targetLoc);
					
					if (successMessage != null) {
						player.message(successMessage);
					}
					
					if (worldExitHook != null) {
						final WorldEnterHook worldEnterHook = new WorldEnterHook(worldExitHook);
						worldEnterHook.call();
					}
				}
				// }, 500, TimeUnit.MILLISECONDS);
				// // if (successMessage != null) {
				// // player.message(successMessage);
				// // }
				// }
			}
		}, TELEPORT_DELAY_SECONDS, TimeUnit.SECONDS);
	}
	
	// // @HookHandler
	// public void onTeleport2(final TeleportHook hook) {
	// final Player player = hook.getPlayer();
	//
	// final String playerId = player.getUUIDString();
	// if (!movingPlayerMap.containsKey(playerId)) {
	// Location curLoc = player.getLocation();
	// final Location destLoc = hook.getDestination();
	//
	// // Set location to where they died, if in deadPlayerMap
	// if (deadPlayerMap.containsKey(playerId)) {
	// curLoc = deadPlayerMap.remove(playerId);
	// }
	//
	// if (curLoc.getWorld() != destLoc.getWorld()) {
	// final WorldChangeCause reason;
	// if (player.getHealth() == 0) {
	// reason = WorldChangeCause.DEATH;
	// } else {
	// reason = WorldChangeCause.COMMAND;
	// }
	//
	// final WorldExitHook worldExitHook = new WorldExitHook(player, curLoc.getWorld(), curLoc,
	// destLoc, reason);
	// Canary.hooks().callHook(worldExitHook);
	// movingPlayerMap.put(player.getUUIDString(), worldExitHook);
	//
	// hook.setCanceled();
	// preloadChunk(worldExitHook.getToLocation());
	// waitTeleport(player, worldExitHook.getToLocation(), null, null);
	// return;
	// }
	// }
	//
	// switch (hook.getTeleportReason()) {
	// case RESPAWN:
	// if (config.exactSpawn()) {
	// if (!isBedRespawn(player)) {
	// hook.setCanceled();
	// final Location loc = hook.getDestination().getWorld().getSpawnLocation();
	// preloadChunk(loc);
	// waitTeleport(player, loc, null, null);
	// } else {
	// preloadChunk(hook.getDestination());
	// }
	// } else {
	// preloadChunk(hook.getDestination());
	// }
	// break;
	// default:
	// preloadChunk(hook.getDestination());
	// }
	// }
	
	// // @HookHandler
	// public void onRespawned(final PlayerRespawnedHook hook) {
	// final Player player = hook.getPlayer();
	//
	// final WorldExitHook worldExitHook = movingPlayerMap.remove(player.getUUIDString());
	// if (worldExitHook != null) {
	// final Location spawnLoc = hook.getLocation();
	// Canary.hooks().callHook(
	// new WorldEnterHook(player, spawnLoc.getWorld(), worldExitHook.getFromLocation(),
	// spawnLoc, worldExitHook.getReason()));
	// }
	// }
	
	// @HookHandler
	// public void onWorldDeath(final WorldDeathHook hook) {
	// hook.getPlayer().message("You died");
	// hook.setSpawnLocation(hook.getDeathLocation().getWorld().getSpawnLocation());
	// }
	
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
		
		if (!toState.equals(fromState)) {
			manager.savePlayerState(player, fromState, getSaves(fromState));
			player.message(ChatFormat.GOLD + "Saved state " + fromState);
		}
	}
	
	// private void waitTeleport(final Player player, final Location loc, final String successMessage,
	// final String failureMessage) {
	// final Location curLoc = player.getLocation();
	// player.message(ChatFormat.GOLD + "Stand still for " + TELEPORT_DELAY_SECONDS + " seconds...");
	//
	// TaskManager.scheduleDelayedTask(new Runnable() {
	// @Override
	// public void run() {
	// if (curLoc.getDistance(player.getLocation()) == 0) {
	// player.teleportTo(loc);
	// LOG.info("Teleport succeeded");
	// if (successMessage != null) {
	// player.message(successMessage);
	// }
	// } else {
	// LOG.info("Teleport aborted");
	// if (failureMessage != null) {
	// player.message(failureMessage);
	// }
	// }
	// LOG.info("Removed from TP list");
	// tpingPlayerList.remove(player);
	// }
	// }, TELEPORT_DELAY_SECONDS, TimeUnit.SECONDS);
	// }
	//
	// // Credit to SVDragster for this idea, fixed somewhat
	// private boolean isBedRespawn(final Player player) {
	// final Vector3D spawn = new Vector3D(player.getSpawnPosition());
	// final Vector3D worldSpawn = new Vector3D(player.getSpawnPosition().getWorld()
	// .getSpawnLocation());
	// spawn.setY(0);
	// worldSpawn.setY(0);
	// if (spawn.getDistance(worldSpawn) > MULTIPLAYER_SPAWN_RADIUS) {
	// return true;
	// }
	// return false;
	// }
	
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
