package com.eharrison.canary.playerstate.hook;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.hook.Hook;

public class WorldExitHook extends Hook {
	private final Player player;
	private final World world;
	private final Location fromLocation;
	private Location toLocation;
	private final ExitCause cause;
	
	public WorldExitHook(final Player player, final World world, final Location fromLocation,
			final ExitCause cause) {
		this(player, world, fromLocation, null, cause);
	}
	
	public WorldExitHook(final Player player, final World world, final Location fromLocation,
			final Location toLocation, final ExitCause cause) {
		this.player = player;
		this.world = world;
		this.fromLocation = fromLocation;
		this.toLocation = toLocation;
		this.cause = cause;
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
	
	public void setToLocation(final Location toLocation) {
		this.toLocation = toLocation;
	}
	
	public ExitCause getCause() {
		return cause;
	}
	
	public enum ExitCause {
		DEATH, DISCONNECT, COMMAND
	}
}
