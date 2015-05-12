package com.goodformentertainment.canary.playerstate;

import java.util.HashMap;
import java.util.Map;

import net.canarymod.api.world.World;

public final class PlayerState {
	public static final String ALL_WORLDS = "WORLD_ALL";
	public static final String MANAGED_WORLD = "WORLD_MANAGED_";
	
	protected static Map<String, String> managedWorldStates = new HashMap<String, String>();
	protected static Map<String, Save[]> managedStateSaves = new HashMap<String, Save[]>();
	
	public static void registerWorld(final World world, final Save[] saves) {
		final String name = world.getName();
		final String state = MANAGED_WORLD + name;
		managedWorldStates.put(name, state);
		managedStateSaves.put(state, saves);
	}
	
	public static void unregisterWorld(final World world) {
		final String name = world.getName();
		managedStateSaves.remove(managedWorldStates.remove(name));
	}
	
	public enum Save {
		ACHIEVEMENTS("achievements"), STATISTICS("statistics"), INVENTORY("inventory"), GAMEMODE(
				"gamemode"), PREFIX("prefix"), LOCATIONS("locations"), CONDITIONS("conditions");
		
		private final String name;
		
		Save(final String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
}
