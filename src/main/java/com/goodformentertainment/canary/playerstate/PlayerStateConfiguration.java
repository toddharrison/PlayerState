package com.goodformentertainment.canary.playerstate;

import java.util.ArrayList;
import java.util.List;

import net.canarymod.api.world.World;
import net.canarymod.config.Configuration;
import net.visualillusionsent.utils.PropertiesFile;

import com.goodformentertainment.canary.playerstate.PlayerState.Save;

public class PlayerStateConfiguration {
	private final PropertiesFile cfg;
	
	public PlayerStateConfiguration(final PlayerStatePlugin plugin) {
		cfg = Configuration.getPluginConfig(plugin);
	}
	
	public boolean exactSpawn() {
		return cfg.getBoolean("exactSpawn");
	}
	
	public String getDefaultState() {
		return cfg.getString("state.global", PlayerState.ALL_WORLDS);
	}
	
	public String getState(final World world) {
		String state = null;
		final String key = "state.world." + world.getName();
		if (cfg.containsKey(key)) {
			state = cfg.getString(key);
		} else {
			state = getDefaultState();
		}
		return state;
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
