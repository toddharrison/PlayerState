package com.eharrison.canary.playerstate;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.blocks.BlockType;
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
import net.canarymod.tasks.ServerTask;
import net.visualillusionsent.utils.TaskManager;

import com.eharrison.canary.playerstate.PlayerState.Save;
import com.eharrison.canary.playerstate.hook.WorldDeathHook;
import com.eharrison.canary.playerstate.hook.WorldEnterHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook;
import com.eharrison.canary.util.JarUtil;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	private static final int MULTIPLAYER_SPAWN_RADIUS = 16;
	private static final long TELEPORT_DELAY_SECONDS = 3;
	private static final int TELEPORT_DISTANCE_FUDGE = 1;
	
	public static Logman LOG;
	
	private final PlayerStateConfiguration config;
	private final PlayerStateManager manager;
	private final PlayerStateCommand command;
	
	private final Collection<String> connectingPlayers;
	private final Map<String, WorldExitHook> exitingPlayers;
	private final Map<String, Location> deadPlayers;
	private final Map<String, Location> finalLocations;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.LOG = getLogman();
		
		connectingPlayers = Collections.synchronizedCollection(new HashSet<String>());
		exitingPlayers = Collections.synchronizedMap(new HashMap<String, WorldExitHook>());
		deadPlayers = Collections.synchronizedMap(new HashMap<String, Location>());
		finalLocations = Collections.synchronizedMap(new HashMap<String, Location>());
		
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
		final World world = player.getWorld();
		final Location toLoc = player.getLocation();
		
		new WorldEnterHook(player, world, null, toLoc).call();
		
		if (hook.isFirstConnection()) {
			if (config.exactSpawn()) {
				player.teleportTo(Canary.getServer().getDefaultWorld().getSpawnLocation());
			}
		}
		
		connectingPlayers.add(player.getUUIDString());
	}
	
	@HookHandler
	public void onDisconnection(final DisconnectionHook hook) throws DatabaseWriteException {
		final Player player = hook.getPlayer();
		final World world = player.getWorld();
		final Location fromLoc = player.getLocation();
		
		// final String state = getState(player.getWorld());
		// manager.savePlayerState(player, state, getSaves(state));
		
		new WorldExitHook(player, world, fromLoc, null).call();
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
		// LOG.info("DIED: " + player.getLocation());
	}
	
	// @HookHandler
	// public void onRespawned(final PlayerRespawnedHook hook) {
	// final Player player = hook.getPlayer();
	// final String uuid = player.getUUIDString();
	//
	// LOG.info("RESPAWNED");
	// LOG.info("  BEDSPAWN: " + isBedRespawn(hook.getLocation()));
	// LOG.info("  DEAD: " + deadPlayers.containsKey(uuid));
	//
	// // if (config.exactSpawn()) {
	// // if (!isBedRespawn(player)) {
	// // player.teleportTo(player.getLocation().getWorld().getSpawnLocation());
	// // }
	// // }
	// //
	// // if (exitingPlayers.containsKey(uuid)) {
	// // final WorldEnterHook worldEnter = new WorldEnterHook(exitingPlayers.remove(uuid));
	// // worldEnter.call();
	// // }
	// }
	
	@HookHandler
	public void onRespawned(final PlayerRespawnedHook hook) {
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		
		// LOG.info("RESPAWNED: " + hook.getLocation());
		
		if (exitingPlayers.containsKey(uuid)) {
			final WorldEnterHook worldEnter = new WorldEnterHook(exitingPlayers.remove(uuid));
			worldEnter.call();
		}
		
		final Location finalLoc = finalLocations.remove(uuid);
		if (finalLoc != null) {
			Canary.getServer().addSynchronousTask(new ServerTask(this, 10) {
				@Override
				public void run() {
					player.teleportTo(finalLoc);
				}
			});
		}
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) {
		// LOG.info("TELEPORT " + hook.getTeleportReason());
		
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		final Location curLoc = hook.getCurrentLocation();
		final Location destination = hook.getDestination();
		
		switch (hook.getTeleportReason()) {
			case BED:
			case MOUNT_CHANGE:
			case MOVEMENT:
				// LOG.info("  CURLOC: " + curLoc);
				// LOG.info("  DESTINATION: " + destination);
				// LOG.info("  SPAWN: " + player.getSpawnPosition());
				break;
			case COMMAND:
			case PLUGIN:
			case WARP:
				if (curLoc.getWorld().equals(destination.getWorld())) {
				} else {
					final WorldExitHook worldExit = new WorldExitHook(player, curLoc.getWorld(), curLoc,
							destination);
					worldExit.call();
					exitingPlayers.put(uuid, worldExit);
				}
				break;
			case RESPAWN:
				// if (curLoc.equals(destination)) {
				// LOG.info("  CURLOC == DEST");
				// }
				if (connectingPlayers.remove(uuid)) {
					// LOG.info("  CONNECTING");
				} else if (exitingPlayers.containsKey(uuid)) {
					// LOG.info("  EXITING");
				} else if (deadPlayers.containsKey(uuid)) {
					// LOG.info("  ISDEAD");
					
					final Location deadLoc = deadPlayers.remove(uuid);
					final Location spawnLoc = destination;
					
					final WorldDeathHook worldDeath = new WorldDeathHook(player, deadLoc, spawnLoc);
					worldDeath.call();
					if (!spawnLoc.equals(worldDeath.getSpawnLocation())) {
						// LOG.info("  OVERRIDESPAWN");
						finalLocations.put(uuid, worldDeath.getSpawnLocation());
					}
					
					if (isBedRespawn(spawnLoc)) {
						// LOG.info("  GOTOBED");
						
						final Location bedLoc = player.getSpawnPosition();
						if (spawnLoc.getWorld().getBlockAt(bedLoc).getType() == BlockType.Bed) {
							// LOG.info("    PRESENT");
							
							if (!deadLoc.getWorld().equals(spawnLoc.getWorld())) {
								final WorldExitHook worldExit = new WorldExitHook(player, deadLoc.getWorld(),
										deadLoc, spawnLoc);
								worldExit.call();
								exitingPlayers.put(uuid, worldExit);
							}
						} else {
							// LOG.info("    MISSING");
							
							if (!deadLoc.getWorld().equals(spawnLoc.getWorld())) {
								final WorldExitHook worldExit = new WorldExitHook(player, deadLoc.getWorld(),
										deadLoc, spawnLoc.getWorld().getSpawnLocation());
								worldExit.call();
								exitingPlayers.put(uuid, worldExit);
							}
							
							hook.setCanceled();
							player.teleportTo(spawnLoc.getWorld().getSpawnLocation());
						}
						
						// LOG.info("  CURLOC: " + curLoc);
						// LOG.info("  DESTINATION: " + destination);
						// LOG.info("  SPAWN: " + player.getSpawnPosition());
					} else {
						// LOG.info("  GOTOSPAWN");
						
						if (!deadLoc.getWorld().equals(spawnLoc.getWorld())) {
							final WorldExitHook worldExit = new WorldExitHook(player, deadLoc.getWorld(),
									deadLoc, spawnLoc.getWorld().getSpawnLocation());
							worldExit.call();
							exitingPlayers.put(uuid, worldExit);
						}
						
						if (config.exactSpawn()) {
							hook.setCanceled();
							player.teleportTo(spawnLoc.getWorld().getSpawnLocation());
						}
					}
				}
				break;
			case PORTAL:
			case UNDEFINED:
				// LOG.info("  CURLOC: " + curLoc);
				// LOG.info("  DESTINATION: " + destination);
				// LOG.info("  SPAWN: " + player.getSpawnPosition());
				break;
		}
	}
	
	// TODO: For testing only
	// @HookHandler
	// public void onWorldDeath(final WorldDeathHook hook) {
	// final Location newLoc = Canary.getServer().getDefaultWorld().getSpawnLocation();
	// hook.setSpawnLocation(newLoc);
	// }
	
	@HookHandler
	public void onWorldEnter(final WorldEnterHook hook) throws DatabaseReadException,
			DatabaseWriteException {
		final Player player = hook.getPlayer();
		final World toWorld = hook.getToLocation().getWorld();
		final String toState = getState(toWorld);
		String fromState = null;
		if (hook.getFromLocation() != null) {
			fromState = getState(hook.getFromLocation().getWorld());
		}
		
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
		String toState = null;
		if (hook.getToLocation() != null) {
			toState = getState(hook.getToLocation().getWorld());
		}
		
		player.message(ChatFormat.GRAY + "Exited world " + hook.getWorld().getName());
		
		if (!fromState.equals(toState)) {
			manager.savePlayerState(player, fromState, getSaves(fromState));
			player.message(ChatFormat.GOLD + "Saved state " + fromState);
		}
	}
	
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
	
	private boolean isBedRespawn(final Location loc) {
		boolean isBedRespawn = false;
		
		final Vector3D v = new Vector3D(loc);
		final Vector3D spawnV = new Vector3D(loc.getWorld().getSpawnLocation());
		v.setY(0);
		spawnV.setY(0);
		if (v.getSquareDistance(spawnV) > MULTIPLAYER_SPAWN_RADIUS * MULTIPLAYER_SPAWN_RADIUS) {
			isBedRespawn = true;
		}
		
		return isBedRespawn;
	}
	
	// private void preloadChunk(final Location loc) {
	// final World world = loc.getWorld();
	// if (!world.isChunkLoaded(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
	// loc.getWorld().loadChunk(loc);
	// }
	// }
}
