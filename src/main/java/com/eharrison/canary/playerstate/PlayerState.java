package com.eharrison.canary.playerstate;

import java.util.HashMap;
import java.util.Map;

import net.canarymod.api.world.World;

public final class PlayerState {
	public static final String WORLD_PREFIX = "WORLD_";
	public static final String ALL_WORLDS = "WORLD_ALL";
	
	protected static Map<String, Save[]> registeredWorlds = new HashMap<String, Save[]>();
	
	public static void registerWorld(final World world, final Save[] saves) {
		registeredWorlds.put(world.getName(), saves);
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
