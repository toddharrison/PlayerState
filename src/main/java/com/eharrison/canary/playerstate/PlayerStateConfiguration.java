package com.eharrison.canary.playerstate;

import java.util.ArrayList;
import java.util.List;

import net.canarymod.config.Configuration;
import net.visualillusionsent.utils.PropertiesFile;

import com.eharrison.canary.playerstate.PlayerState.Save;

public class PlayerStateConfiguration {
	private final PropertiesFile cfg;
	
	public PlayerStateConfiguration(final PlayerStatePlugin plugin) {
		cfg = Configuration.getPluginConfig(plugin);
	}
	
	public boolean automateOnWorldChange() {
		return cfg.getBoolean("automate", false);
	}
	
	public Save[] getSaves(final String state) {
		final List<Save> saves = new ArrayList<Save>();
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.ACHIEVEMENTS, false)) {
			saves.add(PlayerState.Save.ACHIEVEMENTS);
		}
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.CONDITIONS, true)) {
			saves.add(PlayerState.Save.CONDITIONS);
		}
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.GAMEMODE, true)) {
			saves.add(PlayerState.Save.GAMEMODE);
		}
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.INVENTORY, true)) {
			saves.add(PlayerState.Save.INVENTORY);
		}
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.LOCATIONS, true)) {
			saves.add(PlayerState.Save.LOCATIONS);
		}
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.PREFIX, false)) {
			saves.add(PlayerState.Save.PREFIX);
		}
		if (cfg.getBoolean("save." + state + "." + PlayerState.Save.STATISTICS, false)) {
			saves.add(PlayerState.Save.STATISTICS);
		}
		return saves.toArray(new Save[saves.size()]);
	}
}
