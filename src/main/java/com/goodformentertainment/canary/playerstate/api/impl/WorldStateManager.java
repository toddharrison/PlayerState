package com.goodformentertainment.canary.playerstate.api.impl;

import java.util.HashMap;
import java.util.Map;

import net.canarymod.api.world.World;

import com.goodformentertainment.canary.playerstate.api.IWorldStateManager;
import com.goodformentertainment.canary.playerstate.api.SaveState;

public class WorldStateManager implements IWorldStateManager {
	private final Map<String, String> managedWorldStates;
	private final Map<String, SaveState[]> managedStateSaves;
	
	public WorldStateManager() {
		managedWorldStates = new HashMap<String, String>();
		managedStateSaves = new HashMap<String, SaveState[]>();
	}
	
	@Override
	public void registerWorld(final World world, final SaveState[] saves) {
		final String name = world.getName();
		final String state = MANAGED_WORLD + name;
		managedWorldStates.put(name, state);
		managedStateSaves.put(state, saves);
	}
	
	@Override
	public void unregisterWorld(final World world) {
		final String name = world.getName();
		managedStateSaves.remove(managedWorldStates.remove(name));
	}
	
	@Override
	public String getManagedWorldState(final World world) {
		return managedWorldStates.get(world.getName());
	}
	
	@Override
	public SaveState[] getManagedStateSaves(final String state) {
		return managedStateSaves.get(state);
	}
}
