package com.goodformentertainment.canary.playerstate;

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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import com.goodformentertainment.canary.playerstate.api.IWorldStateManager;
import com.goodformentertainment.canary.playerstate.api.SaveState;
import com.goodformentertainment.canary.playerstate.api.impl.WorldStateManager;
import com.goodformentertainment.canary.playerstate.hook.WorldDeathHook;
import com.goodformentertainment.canary.playerstate.hook.WorldEnterHook;
import com.goodformentertainment.canary.playerstate.hook.WorldExitHook;
import com.goodformentertainment.canary.util.JarUtil;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	private static final int MULTIPLAYER_SPAWN_RADIUS = 16;
	private static final long TELEPORT_DELAY_SECONDS = 3;
	private static final int TELEPORT_DISTANCE_FUDGE = 1;
	
	public static Logman LOG;
	
	private static final IWorldStateManager worldStateManager = new WorldStateManager();
	
	/**
	 * Get the WorldManager from the PlayerStatePlugin.
	 * 
	 * @return The WorldManager.
	 */
	public static IWorldStateManager getWorldManager() {
		return worldStateManager;
	}
	
	private PlayerStateConfiguration config;
	private PlayerStateManager manager;
	private PlayerStateCommand command;
	
	private Collection<String> connectingPlayers;
	private Map<String, WorldExitHook> exitingPlayers;
	private Map<String, Location> deadPlayers;
	private Map<String, Location> finalLocations;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.LOG = getLogman();
	}
	
	@Override
	public boolean enable() {
		boolean success = true;
		
		config = new PlayerStateConfiguration(this);
		setLoggingLevel(config.getLoggingLevel());
		
		LOG.info("Enabling " + getName() + " Version " + getVersion());
		LOG.info("Authored by " + getAuthor());
		
		connectingPlayers = Collections.synchronizedCollection(new HashSet<String>());
		exitingPlayers = Collections.synchronizedMap(new HashMap<String, WorldExitHook>());
		deadPlayers = Collections.synchronizedMap(new HashMap<String, Location>());
		finalLocations = Collections.synchronizedMap(new HashMap<String, Location>());
		
		try {
			JarUtil.exportResource(this, "PlayerState.cfg", new File("config/PlayerState"));
		} catch (final IOException e) {
			LOG.warn("Failed to create the default configuration file.", e);
		}
		
		manager = new PlayerStateManager(this);
		command = new PlayerStateCommand(manager);
		
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
		
		config = null;
		manager = null;
		command = null;
		
		connectingPlayers = null;
		exitingPlayers = null;
		deadPlayers = null;
		finalLocations = null;
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
		if (command[0].equalsIgnoreCase("/spawn")) {
			hook.setCanceled();
			
			// Do not allow spawn command to nether dimension
			final Player player = hook.getPlayer();
			if (command[command.length - 1].matches(".*(?i)(NETHER)$")) {
				player.message(ChatFormat.RED + "Spawning to the nether is verboten.");
			} else {
				World world = player.getWorld();
				if (command.length > 1) {
					world = Canary.getServer().getWorld(command[1]);
				}
				if (world == null) {
					player.message(ChatFormat.RED + "Invalid world specified.");
				} else {
					final Location spawn = world.getSpawnLocation();
					delayedTeleport(player, spawn, ChatFormat.YELLOW + "Teleported to spawn");
				}
			}
		} else if (command[0].equalsIgnoreCase("/home")) {
			hook.setCanceled();
			
			final Player player = hook.getPlayer();
			Player targetPlayer = player;
			if (command.length > 1) {
				targetPlayer = Canary.getServer().getPlayer(command[1]);
			}
			// TODO handle offline players
			final Location loc = targetPlayer.getHome();
			delayedTeleport(player, loc, ChatFormat.RED + "Going home");
		} else if (command[0].equalsIgnoreCase("/tp")) {
			// TODO: Don't display the message until after success?
			// foo(player, loc, "Teleported " + targetPlayer.getName() + " to " + loc);
		}
	}
	
	@HookHandler
	public void onDeath(final PlayerDeathHook hook) {
		final Player player = hook.getPlayer();
		deadPlayers.put(player.getUUIDString(), player.getLocation());
		
		LOG.debug("Player " + player.getName() + " died at: " + player.getLocation());
	}
	
	@HookHandler
	public void onRespawned(final PlayerRespawnedHook hook) {
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		
		LOG.info("Player " + player.getName() + " respawned at: " + hook.getLocation());
		
		if (exitingPlayers.containsKey(uuid)) {
			final WorldEnterHook worldEnter = new WorldEnterHook(exitingPlayers.remove(uuid));
			if (worldEnter.getWorld() == player.getWorld()) {
				worldEnter.call();
			} else {
				LOG.error("Player " + player.getName() + " is not in the expected world "
						+ worldEnter.getWorld().getName());
			}
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
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		final Location curLoc = hook.getCurrentLocation();
		final Location destination = hook.getDestination();
		
		LOG.debug("Player " + player.getName() + " teleported from " + curLoc + " to " + destination);
		
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
					LOG.debug("  CONNECTING");
				} else if (exitingPlayers.containsKey(uuid)) {
					LOG.debug("  EXITING");
				} else if (deadPlayers.containsKey(uuid)) {
					LOG.debug("  ISDEAD");
					
					final Location deadLoc = deadPlayers.remove(uuid);
					final Location spawnLoc = destination;
					
					final WorldDeathHook worldDeath = new WorldDeathHook(player, deadLoc, spawnLoc);
					worldDeath.call();
					if (!spawnLoc.equals(worldDeath.getSpawnLocation())) {
						LOG.debug("  OVERRIDESPAWN");
						finalLocations.put(uuid, worldDeath.getSpawnLocation());
					}
					
					if (isBedRespawn(spawnLoc)) {
						LOG.debug("  GOTOBED");
						
						final Location bedLoc = player.getSpawnPosition();
						if (spawnLoc.getWorld().getBlockAt(bedLoc).getType() == BlockType.Bed) {
							LOG.debug("    PRESENT");
							
							if (!deadLoc.getWorld().equals(spawnLoc.getWorld())) {
								final WorldExitHook worldExit = new WorldExitHook(player, deadLoc.getWorld(),
										deadLoc, spawnLoc);
								worldExit.call();
								exitingPlayers.put(uuid, worldExit);
							}
						} else {
							LOG.debug("    MISSING");
							
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
						LOG.debug("  GOTOSPAWN");
						
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
		String state = worldStateManager.getManagedWorldState(world);
		if (state == null) {
			state = config.getState(world);
		}
		return state;
	}
	
	private SaveState[] getSaves(final String state) {
		SaveState[] saves = worldStateManager.getManagedStateSaves(state);
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
	
	private void setLoggingLevel(final String level) {
		if (level != null) {
			final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			final Configuration config = ctx.getConfiguration();
			final LoggerConfig loggerConfig = config.getLoggerConfig(LOG.getName());
			loggerConfig.setLevel(Level.toLevel(level));
			ctx.updateLoggers();
		}
	}
}
