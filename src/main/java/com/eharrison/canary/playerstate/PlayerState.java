package com.eharrison.canary.playerstate;

import java.util.HashSet;
import java.util.Set;

import net.canarymod.api.world.World;

public final class PlayerState {
	public static final String WORLD_PREFIX = "WORLD_";
	public static final String ALL_WORLDS = "WORLD_ALL";
	
	protected static IPlayerStateManager playerStateManager;
	protected static Set<String> registeredWorlds = new HashSet<String>();
	
	public static IPlayerStateManager getPlayerStateManager() {
		return playerStateManager;
	}
	
	public static void registerWorld(final World world) {
		registeredWorlds.add(world.getName());
	}
	
	public static void unregisterWorld(final World world) {
		registeredWorlds.remove(world.getName());
	}
}
