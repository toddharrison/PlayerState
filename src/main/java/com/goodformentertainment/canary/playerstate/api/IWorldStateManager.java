package com.goodformentertainment.canary.playerstate.api;

import net.canarymod.api.world.World;

public interface IWorldStateManager {
	public static final String ALL_WORLDS = "WORLD_ALL";
	public static final String MANAGED_WORLD = "WORLD_MANAGED_";
	
	void registerWorld(World world, final SaveState[] saves);
	
	void unregisterWorld(World world);
	
	String getManagedWorldState(World world);
	
	SaveState[] getManagedStateSaves(String state);
}
