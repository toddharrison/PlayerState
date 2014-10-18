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
}
