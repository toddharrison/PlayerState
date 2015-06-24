package com.goodformentertainment.canary.playerstate.hook;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.position.Location;
import net.canarymod.hook.Hook;

public class WorldDeathHook extends Hook {
    private final Player player;
    private final Location deathLocation;
    private Location spawnLocation;

    public WorldDeathHook(final Player player, final Location deathLocation,
                          final Location spawnLocation) {
        this.player = player;
        this.deathLocation = deathLocation;
        this.spawnLocation = spawnLocation;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getDeathLocation() {
        return deathLocation;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(final Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }
}
