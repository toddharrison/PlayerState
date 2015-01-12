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
	
	public static void registerWorld(final World world, final Save... saves) {
		registeredWorlds.add(world.getName());
		for (final Save save : saves) {
			// TODO: save the saves associated with a world
			// TODO: provide a function to check save properties
			// TODO: combine with configuration parameters?
		}
	}
	
	public static void unregisterWorld(final World world) {
		registeredWorlds.remove(world.getName());
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
