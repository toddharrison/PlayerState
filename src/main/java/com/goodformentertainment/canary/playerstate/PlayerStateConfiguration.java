package com.goodformentertainment.canary.playerstate;

import com.goodformentertainment.canary.playerstate.api.IWorldStateManager;
import com.goodformentertainment.canary.playerstate.api.SaveState;
import net.canarymod.api.world.World;
import net.canarymod.config.Configuration;
import net.visualillusionsent.utils.PropertiesFile;

import java.util.ArrayList;
import java.util.List;

public class PlayerStateConfiguration {
    private final PropertiesFile cfg;

    public PlayerStateConfiguration(final PlayerStatePlugin plugin) {
        cfg = Configuration.getPluginConfig(plugin);
    }

    public boolean exactSpawn() {
        return cfg.getBoolean("exactSpawn");
    }

    public String getDefaultState() {
        return cfg.getString("state.global", IWorldStateManager.ALL_WORLDS);
    }

    public String getState(final World world) {
        final String state;
        final String key = "state.world." + world.getName();
        if (cfg.containsKey(key)) {
            state = cfg.getString(key);
        } else {
            state = getDefaultState();
        }
        return state;
    }

    public SaveState[] getSaves(final String state) {
        final List<SaveState> saves = new ArrayList<SaveState>();
        if (cfg.getBoolean("save." + state + "." + SaveState.ACHIEVEMENTS, false)) {
            saves.add(SaveState.ACHIEVEMENTS);
        }
        if (cfg.getBoolean("save." + state + "." + SaveState.CONDITIONS, true)) {
            saves.add(SaveState.CONDITIONS);
        }
        if (cfg.getBoolean("save." + state + "." + SaveState.GAMEMODE, true)) {
            saves.add(SaveState.GAMEMODE);
        }
        if (cfg.getBoolean("save." + state + "." + SaveState.INVENTORY, true)) {
            saves.add(SaveState.INVENTORY);
        }
        if (cfg.getBoolean("save." + state + "." + SaveState.LOCATIONS, true)) {
            saves.add(SaveState.LOCATIONS);
        }
        if (cfg.getBoolean("save." + state + "." + SaveState.PREFIX, false)) {
            saves.add(SaveState.PREFIX);
        }
        if (cfg.getBoolean("save." + state + "." + SaveState.STATISTICS, false)) {
            saves.add(SaveState.STATISTICS);
        }
        return saves.toArray(new SaveState[saves.size()]);
    }

    public String getLoggingLevel() {
        String level = null;
        final String key = "log.level";
        if (cfg.containsKey(key)) {
            level = cfg.getString(key);
        }
        return level;
    }
}
