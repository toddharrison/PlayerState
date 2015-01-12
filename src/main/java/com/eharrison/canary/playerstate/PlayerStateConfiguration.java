package com.eharrison.canary.playerstate;

import net.canarymod.config.Configuration;
import net.visualillusionsent.utils.PropertiesFile;

public class PlayerStateConfiguration {
	private final PropertiesFile cfg;
	
	public PlayerStateConfiguration(final PlayerStatePlugin plugin) {
		cfg = Configuration.getPluginConfig(plugin);
	}
	
	public boolean automateOnWorldChange() {
		return cfg.getBoolean("automate", false);
	}
	
	public boolean saveAchievements(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.ACHIEVEMENTS, false);
	}
	
	public boolean saveStatistics(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.STATISTICS, false);
	}
	
	public boolean saveGameMode(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.GAMEMODE, false);
	}
	
	public boolean savePrefix(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.PREFIX, false);
	}
	
	public boolean saveLocations(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.LOCATIONS, false);
	}
	
	public boolean saveConditions(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.CONDITIONS, true);
	}
	
	public boolean saveInventory(final String state) {
		// TODO: Add global setting
		return cfg.getBoolean("save." + state + "." + PlayerState.Save.INVENTORY, true);
	}
}
