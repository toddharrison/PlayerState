package com.eharrison.canary.playerstate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.api.world.position.Vector3D;
import net.canarymod.chat.ChatFormat;
import net.canarymod.hook.HookHandler;
import net.canarymod.hook.player.ConnectionHook;
import net.canarymod.hook.player.DisconnectionHook;
import net.canarymod.hook.player.PlayerRespawnedHook;
import net.canarymod.hook.player.PlayerRespawningHook;
import net.canarymod.hook.player.TeleportHook;
import net.canarymod.logger.Logman;
import net.canarymod.plugin.Plugin;
import net.canarymod.plugin.PluginListener;

import com.eharrison.canary.playerstate.hook.WorldChangeCause;
import com.eharrison.canary.playerstate.hook.WorldEnterHook;
import com.eharrison.canary.playerstate.hook.WorldExitHook;

public class PlayerStatePlugin extends Plugin implements PluginListener {
	private static final int MULTIPLAYER_SPAWN_RADIUS = 5;
	
	public static Logman LOG;
	
	private final boolean exactSpawn = true;
	private final Map<String, WorldExitHook> movingPlayerMap;
	private final boolean manageAllWorlds = false;
	private final Collection<String> managedWorldList;
	
	public PlayerStatePlugin() {
		PlayerStatePlugin.LOG = getLogman();
		movingPlayerMap = new HashMap<String, WorldExitHook>();
		managedWorldList = new ArrayList<String>();
		
		// TODO
		managedWorldList.add("default");
	}
	
	@Override
	public boolean enable() {
		final boolean success = true;
		
		LOG.info("Enabling " + getName() + " Version " + getVersion());
		LOG.info("Authored by " + getAuthor());
		
		Canary.hooks().registerListener(this, this);
		
		return success;
	}
	
	@Override
	public void disable() {
		LOG.info("Disabling " + getName());
		
		Canary.commands().unregisterCommands(this);
		Canary.hooks().unregisterPluginListeners(this);
	}
	
	@HookHandler
	public void onConnection(final ConnectionHook hook) {
		final Player player = hook.getPlayer();
		final World world = player.getWorld();
		
		if (hook.isFirstConnection()) {
			if (exactSpawn) {
				final Location loc = Canary.getServer().getDefaultWorld().getSpawnLocation();
				preloadChunk(loc);
				player.teleportTo(loc);
			}
		}
		
		if (isManagedWorld(world)) {
			hook.getPlayer().message(ChatFormat.GOLD + "Loading your state for world " + world.getName());
		} else {
			hook.getPlayer().message(ChatFormat.GOLD + "Loading your global state");
		}
	}
	
	@HookHandler
	public void onDisconnection(final DisconnectionHook hook) {
		final Player player = hook.getPlayer();
		final World world = player.getWorld();
		
		if (isManagedWorld(world)) {
			LOG.info("Saving " + player.getName() + " state for world " + world.getName());
		} else {
			LOG.info("Saving " + player.getName() + " global state");
		}
	}
	
	@HookHandler
	public void onTeleport(final TeleportHook hook) {
		final Player player = hook.getPlayer();
		switch (hook.getTeleportReason()) {
			case RESPAWN:
				if (exactSpawn) {
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
				if (exactSpawn) {
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
	public void onWorldEnter(final WorldEnterHook hook) {
		final World world = hook.getWorld();
		LOG.info(hook.getPlayer().getName() + " entered " + hook.getWorld().getName());
		if (isManagedWorld(world)) {
			hook.getPlayer().message(ChatFormat.GOLD + "Loading your state for world " + world.getName());
		} else if (isManagedWorld(hook.getFromLocation().getWorld())) {
			hook.getPlayer().message(ChatFormat.GOLD + "Loading your global state");
		}
	}
	
	@HookHandler
	public void onWorldExit(final WorldExitHook hook) {
		final World world = hook.getWorld();
		LOG.info(hook.getPlayer().getName() + " left " + hook.getWorld().getName() + " for "
				+ hook.getToLocation().getWorld().getName());
		
		if (hook.getReason() == WorldChangeCause.DEATH) {
			hook.getPlayer().message(ChatFormat.GOLD + "Applying death penalty");
		}
		
		if (isManagedWorld(world)) {
			hook.getPlayer().message(ChatFormat.GOLD + "Saving your state for world " + world.getName());
		} else if (isManagedWorld(hook.getToLocation().getWorld())) {
			hook.getPlayer().message(ChatFormat.GOLD + "Saving your global state");
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
	
	private boolean isManagedWorld(final World world) {
		return manageAllWorlds || managedWorldList.contains(world.getName());
	}
	
	private void preloadChunk(final Location loc) {
		final World world = loc.getWorld();
		if (!world.isChunkLoaded(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
			loc.getWorld().loadChunk(loc);
		}
	}
}
