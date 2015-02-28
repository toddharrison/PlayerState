package com.eharrison.canary.playerstate.hook;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.hook.Hook;

public class WorldExitHook extends Hook {
	private final Player player;
	private final World world;
	private final Location fromLocation;
	private final Location toLocation;
	private final WorldChangeCause reason;
	
	public WorldExitHook(final Player player, final World world, final Location fromLocation,
			final Location toLocation, final WorldChangeCause reason) {
		this.player = player;
		this.world = world;
		this.fromLocation = fromLocation;
		this.toLocation = toLocation;
		this.reason = reason;
	}
	
	public Player getPlayer() {
		return player;
	}
	
	public World getWorld() {
		return world;
	}
	
	public Location getFromLocation() {
		return fromLocation;
	}
	
	public Location getToLocation() {
		return toLocation;
	}
	
	public WorldChangeCause getReason() {
		return reason;
	}
}
