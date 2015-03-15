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
		
		if (hook.isFirstConnection()) {
			final Location loc = Canary.getServer().getDefaultWorld().getSpawnLocation();
			
			player.setSpawnPosition(loc);
			manager.savePlayerState(player, state, getSaves(state));
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
	}
	
	@HookHandler
	public void onRespawned(final PlayerRespawnedHook hook) {
		final Player player = hook.getPlayer();
		final String uuid = player.getUUIDString();
		
		if (config.exactSpawn()) {
			if (!isBedRespawn(player)) {
				player.teleportTo(player.getLocation().getWorld().getSpawnLocation());
			}
		}
		
		if (exitingPlayers.containsKey(uuid)) {
			final WorldEnterHook worldEnter = new WorldEnterHook(exitingPlayers.remove(uuid));
			worldEnter.call();
		}
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) {
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
		}
	}
	
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
