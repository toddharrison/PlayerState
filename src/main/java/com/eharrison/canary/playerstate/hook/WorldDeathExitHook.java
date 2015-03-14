package com.eharrison.canary.playerstate.hook;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.hook.Hook;

public class WorldDeathExitHook extends Hook {
	private final Player player;
	private final World world;
	private final Location deathLocation;
	private final Location spawnLocation;
	
	public WorldDeathExitHook(final Player player, final World world, final Location deathLocation,
			final Location spawnLocation) {
		this.player = player;
		this.world = world;
		this.deathLocation = deathLocation;
		this.spawnLocation = spawnLocation;
	}
	
	public Player getPlayer() {
		return player;
	}
	
	public World getWorld() {
		return world;
	}
	
	public Location getDeathLocation() {
		return deathLocation;
	}
	
	public Location getSpawnLocation() {
		return spawnLocation;
	}
}
