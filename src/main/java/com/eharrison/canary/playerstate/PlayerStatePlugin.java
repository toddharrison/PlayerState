package com.eharrison.canary.playerstate;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
import net.canarymod.hook.command.PlayerCommandHook;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.PlayerDeathHook;
import net.canarymod.hook.player.PlayerRespawnedHook;
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
	private static final int MULTIPLAYER_SPAWN_RADIUS = 10;
	private static final long TELEPORT_DELAY_SECONDS = 3;
	private static final int TELEPORT_DISTANCE_FUDGE = 1;
	
	public static Logman LOG;
	
	private final PlayerStateConfiguration config;
	private final PlayerStateManager manager;
	private final PlayerStateCommand command;
	
	private final Map<String, WorldExitHook> exitingPlayers;
	private final Map<String, Location> deadPlayers;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.LOG = getLogman();
		
		exitingPlayers = Collections.synchronizedMap(new HashMap<String, WorldExitHook>());
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
	public void onConnection(final ConnectionHook hook) throws DatabaseReadException,
			DatabaseWriteException {
		final Player player = hook.getPlayer();
		final String state = getState(player.getWorld());
		
		// TODO call world enter for each below
		if (hook.isFirstConnection()) {
			final Location loc = Canary.getServer().getDefaultWorld().getSpawnLocation();
			
			player.setSpawnPosition(loc);
			manager.savePlayerState(player, state, getSaves(state));
			// manager.setPlayerSpawnLocation(player, state, loc);
			player.setHome(loc);
			
			if (config.exactSpawn()) {
				player.teleportTo(loc);
			}
			
			player.message(ChatFormat.GOLD + "Entering state " + state);
		} else if (manager.loadPlayerState(player, state, getSaves(state))) {
			final Location loc = manager.getPlayerReturnLocation(player, state);
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
			// TODO handle offline players
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
		// LOG.info("DIED IN " + player.getLocation().getWorldName());
	}
	
	// @HookHandler
	// public void onRespawning(final PlayerRespawningHook hook) {
	// LOG.info("RESPAWNING " + hook.getRespawnLocation());
	// }
	
	@HookHandler
	public void onRespawned(final PlayerRespawnedHook hook) {
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		
		// if (deadPlayers.containsKey(uuid)) {
		// final Location deadLoc = deadPlayers.remove(uuid);
		// LOG.info("RESPAWNED FROM DEATH IN " + hook.getLocation().getWorldName());
		// } else {
		// LOG.info("RESPAWNED " + hook.getLocation().getWorldName());
		// }
		
		if (config.exactSpawn()) {
			if (!isBedRespawn(player)) {
				player.teleportTo(player.getLocation().getWorld().getSpawnLocation());
			}
		}
		
		if (exitingPlayers.containsKey(uuid)) {
			final WorldEnterHook worldEnter = new WorldEnterHook(exitingPlayers.remove(uuid));
			worldEnter.call();
		}
		
		// LOG.info(hook.getPlayer().getSpawnPosition());
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) {
		// LOG.info("TP " + hook.getTeleportReason() + " " + hook.getCurrentLocation() + " "
		// + hook.getDestination());
		// LOG.info(hook.getPlayer().getSpawnPosition());
		
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		
		Location curLoc = hook.getCurrentLocation();
		final Location destination = hook.getDestination();
		if (deadPlayers.containsKey(uuid)) {
			
			final WorldDeathHook worldDeathHook = new WorldDeathHook(player, deadPlayers.remove(uuid),
					destination);
			worldDeathHook.call();
			curLoc = worldDeathHook.getSpawnLocation();
		}
		
		if (!curLoc.getWorld().equals(destination.getWorld())) {
			final WorldExitHook worldExit = new WorldExitHook(player, curLoc.getWorld(), curLoc,
					destination);
			exitingPlayers.put(uuid, worldExit);
			worldExit.call();
			
			// LOG.info("CHANGING WORLD " + curLoc.getWorldName() + " TO " + destination.getWorldName());
		}
	}
	
	/*
	 * Spawn: 130.0;63.0;250.0;0.0;0.0;0;default
	 * Home: 132.50399410055581;63.0;261.49786608169495;3.2999952;-194.09993;0;default
	 * Bed: 139.0;63.0;260.0;0.0;0.0;0;default
	 * BedLook: 139.89999389648438;63.6875;260.5;34.349995;-87.450005;0;default
	 * BedNear: 138.5;63.099998474121094;259.5;34.349995;-87.450005;0;default
	 * 
	 * LOGIN
	 * //TP RESPAWN RANDOM RANDOM
	 * //PS SPAWN
	 * TP PLUGIN RANDOM SPAWN
	 * PS SPAWN
	 * TP RESPAWN SPAWN SPAWN
	 * PS SPAWN
	 * 
	 * SPAWN
	 * TP PLUGIN LOC SPAWN
	 * PS SPAWN
	 * 
	 * BEDSPAWN
	 * TP PLUGIN LOC SPAWN
	 * PS BED
	 * 
	 * NOBEDSPAWN
	 * TP PLUGIN LOC SPAWN
	 * PS BED
	 * 
	 * HOME
	 * TP PLUGIN LOC HOME
	 * PS SPAWN
	 * 
	 * BEDHOME
	 * TP PLUGIN LOC HOME
	 * PS BED
	 * 
	 * NOBEDHOME
	 * TP PLUGIN LOC HOME
	 * PS BED
	 * 
	 * NOBEDTP
	 * TP COMMAND LOC TP
	 * PS SPAWN
	 * 
	 * KILL
	 * RESPAWNING null
	 * ISBEDSPAWN false
	 * TP RESPAWN RANDOM RANDOM
	 * PS SPAWN
	 * RESPAWN RANDOM
	 * PS SPAWN
	 * TP MOVEMENT 125.5;64.90199999809265;252.5;0.0;0.0;0;default RANDOM
	 * PS SPAWN
	 * TP MOVEMENT 125.5;64.8039999961853;252.5;0.0;0.0;0;default RANDOM
	 * PS SPAWN
	 * TP MOVEMENT 125.5;64.62955999092101;252.5;0.0;0.0;0;default RANDOM
	 * PS SPAWN
	 * 
	 * BEDKILL
	 * RESPAWNING BED
	 * ISBEDSPAWN false
	 * TP RESPAWN BEDNEAR BEDNEAR
	 * PS BED
	 * RESPAWN BEDNEAR
	 * PS BED
	 * 
	 * NOBEDKILL
	 * RESPAWNING BED
	 * ISBEDSPAWN false
	 * TP RESPAWN RANDOM RANDOM
	 * PS SPAWN
	 * RESPAWN RANDOM
	 * PS SPAWN
	 * 
	 * SLEEP
	 * TP BED BEDLOOK BEDLOOK
	 * PS SPAWN
	 * TP BED BEDNEAR BEDNEAR
	 * PS BED
	 */
	
	// @HookHandler
	// public void onRespawned(final PlayerRespawnedHook hook) throws DatabaseReadException {
	// final Player player = hook.getPlayer();
	// final String uuid = player.getUUIDString();
	//
	// if (deadPlayers.containsKey(uuid)) {
	// LOG.info("RESPAWNED AFTER DEATH");
	//
	// final Location diedLoc = deadPlayers.remove(uuid);
	// final Location respawn = manager.getPlayerSpawnLocation(player, getState(diedLoc.getWorld()));
	//
	// final WorldDeathHook worldDeathHook = new WorldDeathHook(player, diedLoc, respawn);
	// worldDeathHook.call();
	//
	// final Location finalRespawn = worldDeathHook.getSpawnLocation();
	// Canary.getServer().addSynchronousTask(new ServerTask(this, 10, false) {
	// @Override
	// public void run() {
	// player.teleportTo(finalRespawn);
	// }
	// });
	// } else if (exitingPlayers.containsKey(uuid)) {
	// LOG.info("RESPAWNED");
	// //
	// // LOG.info("WORLD ENTER " + hook.getLocation().getWorldName());
	//
	// final WorldExitHook worldExit = exitingPlayers.get(uuid);
	// new WorldEnterHook(worldExit).call();
	//
	// exitingPlayers.remove(uuid);
	// } else {
	// // LOG.info("RESPAWN OTHER");
	// }
	// }
	//
	// @HookHandler
	// public void onTeleport(final TeleportHook hook) throws DatabaseReadException {
	// final Player player = hook.getPlayer();
	// final String uuid = player.getUUIDString();
	//
	// // LOG.info("TP " + hook.getTeleportReason() + " " + hook.getCurrentLocation() + " "
	// // + hook.getDestination());
	//
	// if (hook.getTeleportReason() == TeleportCause.PLUGIN) {
	// final Location curLoc = hook.getCurrentLocation();
	// final Location destination = hook.getDestination();
	//
	// if (curLoc.getWorld() != destination.getWorld()) {
	// // LOG.info("TPing from " + curLoc.getWorldName() + " to " + destination.getWorldName() +
	// // " "
	// // + hook.getTeleportReason());
	//
	// if (!exitingPlayers.containsKey(uuid)) {
	// // tpingPlayers.put(uuid, curLoc);
	// // LOG.info("WORLD EXIT " + curLoc.getWorldName());
	//
	// final WorldExitHook worldExit = new WorldExitHook(player, curLoc.getWorld(), curLoc,
	// destination);
	// exitingPlayers.put(uuid, worldExit);
	// worldExit.call();
	// }
	// }
	// } else if (hook.getTeleportReason() == TeleportCause.BED) {
	// // TODO Already inBed check?
	// final Location bedLoc = hook.getDestination();
	// final String state = getState(bedLoc.getWorld());
	// manager.setPlayerSpawnLocation(player, state, bedLoc);
	// } else if (hook.getTeleportReason() == TeleportCause.RESPAWN
	// && hook.getCurrentLocation().equals(hook.getDestination()) && deadPlayers.containsKey(uuid)) {
	// LOG.info("***** SET BED RESPAWN *****");
	//
	// final Location deadLoc = deadPlayers.get(uuid);
	// final Location destination = hook.getDestination();
	//
	// if (deadLoc.getWorld() != destination.getWorld()) {
	// if (!exitingPlayers.containsKey(uuid)) {
	// final WorldExitHook worldExit = new WorldExitHook(player, deadLoc.getWorld(), deadLoc,
	// destination);
	// exitingPlayers.put(uuid, worldExit);
	// worldExit.call();
	// // deadPlayers.remove(uuid);
	// // new WorldEnterHook(worldExit).call();
	// }
	// }
	// }
	// }
	
	@HookHandler
	public void onWorldEnter(final WorldEnterHook hook) throws DatabaseReadException,
			DatabaseWriteException {
		final Player player = hook.getPlayer();
		final World toWorld = hook.getToLocation().getWorld();
		final String fromState = getState(hook.getFromLocation().getWorld());
		final String toState = getState(toWorld);
		
		player.message(ChatFormat.GRAY + "Entered world " + hook.getWorld().getName());
		
		if (!toState.equals(fromState)) {
			if (manager.loadPlayerState(player, toState, getSaves(toState))) {
				player.message(ChatFormat.GOLD + "Loaded state " + toState);
			} else {
				player.setSpawnPosition(toWorld.getSpawnLocation());
				manager.savePlayerState(player, toState, getSaves(toState));
				// manager.setPlayerSpawnLocation(player, toState, toWorld.getSpawnLocation());
				player.message(ChatFormat.GOLD + "Entering state " + toState);
			}
		} else {
			// Restore things lost during world transition, but not state (like gamemode)
			// TODO fix
			// final int gameMode = manager.getGameMode(player, toState);
			// LOG.info("Restoring game mode to " + gameMode);
			// player.setModeId(gameMode);
		}
	}
	
	@HookHandler
	public void onWorldExit(final WorldExitHook hook) throws DatabaseWriteException {
		final Player player = hook.getPlayer();
		final String fromState = getState(hook.getFromLocation().getWorld());
		final String toState = getState(hook.getToLocation().getWorld());
		
		player.message(ChatFormat.GRAY + "Exited world " + hook.getWorld().getName());
		
		if (!toState.equals(fromState)) {
			manager.savePlayerState(player, fromState, getSaves(fromState));
			player.message(ChatFormat.GOLD + "Saved state " + fromState);
		}
	}
	
	// @HookHandler
	// public void onRespawned(final PlayerRespawnedHook hook) throws DatabaseReadException {
	// final Player player = hook.getPlayer();
	// final String uuid = player.getUUIDString();
	//
	// if (deadPlayers.containsKey(uuid)) {
	// final Location diedLoc = deadPlayers.get(uuid);
	// Location respawn = manager.getPlayerSpawnLocation(player, getState(diedLoc.getWorld()));
	//
	// final WorldDeathHook worldDeathHook = new WorldDeathHook(player, diedLoc, respawn);
	// worldDeathHook.call();
	// respawn = worldDeathHook.getSpawnLocation();
	//
	// new WorldDeathExitHook(player, diedLoc.getWorld(), diedLoc, respawn).call();
	//
	// if (diedLoc.getWorld() != respawn.getWorld()) {
	// LOG.info("NEW WORLD");
	// }
	//
	// LOG.info("Returning to " + respawn);
	// final Location targetLoc = respawn;
	// // Canary.getServer().addSynchronousTask(new ServerTask(this, 10, false) {
	// // @Override
	// // public void run() {
	// deadPlayers.remove(uuid);
	// player.teleportTo(targetLoc);
	// // LOG.info("HERE");
	// // }
	// // });
	// }
	//
	// // if (deadPlayers.containsKey(uuid)) {
	// // LOG.info("MARKED AS DEAD");
	// // if (!respawningPlayers.contains(uuid)) {
	// // LOG.info("NOT YET MARKED AS RESPAWNING");
	// //
	// // respawningPlayers.add(uuid);
	// //
	// // final Location diedLoc = deadPlayers.get(uuid);
	// // Location respawn = manager.getPlayerSpawnLocation(player, getState(diedLoc.getWorld()));
	// //
	// // final WorldDeathHook worldDeathHook = new WorldDeathHook(player, diedLoc, respawn);
	// // worldDeathHook.call();
	// //
	// // respawn = worldDeathHook.getSpawnLocation();
	// //
	// // if (diedLoc.getWorld() != respawn.getWorld()) {
	// // LOG.info("CHANGING WORLDS");
	// //
	// // final WorldExitHook worldExitHook = new WorldExitHook(player, diedLoc.getWorld(),
	// // diedLoc, respawn);
	// // worldExitHook.call();
	// //
	// // new WorldDeathExitHook(player, diedLoc.getWorld(), diedLoc, respawn).call();
	// //
	// // final Location targetLoc = respawn;
	// // Canary.getServer().addSynchronousTask(new ServerTask(this, 10, false) {
	// // @Override
	// // public void run() {
	// // player.teleportTo(targetLoc);
	// // deadPlayers.remove(uuid);
	// // respawningPlayers.remove(uuid);
	// //
	// // final WorldEnterHook worldEnterHook = new WorldEnterHook(worldExitHook);
	// // worldEnterHook.call();
	// // }
	// // });
	// // } else {
	// // LOG.info("RESPAWNING IN SAME WORLD");
	// // }
	// // // } else {
	// // // final Location targetLoc = respawn;
	// // // LOG.info("Teleporting to real spawn");
	// // // LOG.info(targetLoc);
	// // // Canary.getServer().addSynchronousTask(new ServerTask(this, 10, false) {
	// // // @Override
	// // // public void run() {
	// // // player.teleportTo(targetLoc);
	// // // deadPlayers.remove(uuid);
	// // // }
	// // // });
	// // } else {
	// // LOG.info("FLAGGED AS RESPAWNING");
	// // }
	// // } else {
	// // LOG.info("RESPAWNING WITHOUT DEATH");
	// // }
	// }
	//
	// @HookHandler
	// public void onTeleport(final TeleportHook hook) throws DatabaseReadException {
	// final Player player = hook.getPlayer();
	// final String uuid = player.getUUIDString();
	//
	// if (tpingPlayers.contains(uuid)) {
	// // Execute the teleport
	// tpingPlayers.remove(uuid);
	// } else if (deadPlayers.containsKey(uuid)) {
	// // Do nothing
	// LOG.info("DEAD " + hook.getTeleportReason() + " " + hook.getCurrentLocation() + " "
	// + hook.getDestination());
	// } else {
	// final Location curLoc = hook.getCurrentLocation();
	// final Location destination = hook.getDestination();
	// LOG.info("Readying Teleport: " + curLoc + " " + destination);
	//
	// if (curLoc.getWorld() != destination.getWorld()) {
	// tpingPlayers.add(uuid);
	// hook.setCanceled();
	//
	// final WorldExitHook worldExitHook = new WorldExitHook(player, curLoc.getWorld(), curLoc,
	// destination);
	// worldExitHook.call();
	//
	// player.teleportTo(destination);
	//
	// final WorldEnterHook worldEnterHook = new WorldEnterHook(worldExitHook);
	// worldEnterHook.call();
	// } else if (hook.getTeleportReason() == TeleportCause.BED) {
	// // TODO inBed check?
	// final Location bedLoc = hook.getDestination();
	// final String state = getState(bedLoc.getWorld());
	// manager.setPlayerSpawnLocation(player, state, bedLoc);
	// } else {
	// // LOG.info("Teleporting within same world");
	// }
	// }
	// }
	
	private void delayedTeleport(final Player player, final Location destination,
			final String successMessage) {
		final Location curLoc = player.getLocation();
		
		player.message(ChatFormat.GOLD + "Stand still for " + TELEPORT_DELAY_SECONDS
				+ " seconds to teleport...");
		TaskManager.scheduleDelayedTask(new Runnable() {
			@Override
			public void run() {
				if (curLoc.getDistance(player.getLocation()) > TELEPORT_DISTANCE_FUDGE) {
					player.message(ChatFormat.RED + "Teleportation cancelled");
				} else {
					player.teleportTo(destination);
					
					if (successMessage != null) {
						player.message(successMessage);
					}
				}
			}
		}, TELEPORT_DELAY_SECONDS, TimeUnit.SECONDS);
	}
	
	// @HookHandler
	// public void onWorldEnter(final WorldEnterHook hook) throws DatabaseReadException,
	// DatabaseWriteException {
	// final Player player = hook.getPlayer();
	// final World toWorld = hook.getToLocation().getWorld();
	// final String fromState = getState(hook.getFromLocation().getWorld());
	// final String toState = getState(toWorld);
	//
	// if (!toState.equals(fromState)) {
	// if (manager.loadPlayerState(player, toState, getSaves(toState))) {
	// player.message(ChatFormat.GOLD + "Loaded state " + toState);
	// } else {
	// manager.savePlayerState(player, toState, getSaves(toState));
	// manager.setPlayerSpawnLocation(player, toState, toWorld.getSpawnLocation());
	// player.message(ChatFormat.GOLD + "Entering state " + toState);
	// }
	// } else {
	// // Restore things lost transitioning a world, but not a state
	// // TODO fix
	// // final int gameMode = manager.getGameMode(player, toState);
	// // LOG.info("Restoring game mode to " + gameMode);
	// // player.setModeId(gameMode);
	// }
	// }
	
	// @HookHandler
	// public void onWorldExit(final WorldExitHook hook) throws DatabaseWriteException {
	// final Player player = hook.getPlayer();
	// final String fromState = getState(hook.getFromLocation().getWorld());
	// final String toState = getState(hook.getToLocation().getWorld());
	//
	// if (!toState.equals(fromState)) {
	// manager.savePlayerState(player, fromState, getSaves(fromState));
	// player.message(ChatFormat.GOLD + "Saved state " + fromState);
	// }
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
	
	// private void preloadChunk(final Location loc) {
	// final World world = loc.getWorld();
	// if (!world.isChunkLoaded(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
	// loc.getWorld().loadChunk(loc);
	// }
	// }
}
