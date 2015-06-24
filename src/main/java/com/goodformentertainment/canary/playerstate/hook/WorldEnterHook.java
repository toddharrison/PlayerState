package com.goodformentertainment.canary.playerstate.hook;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.hook.Hook;

public class WorldEnterHook extends Hook {
    private final Player player;
    private final World world;
    private final Location fromLocation;
    private final Location toLocation;

    public WorldEnterHook(final WorldExitHook hook) {
        player = hook.getPlayer();
        world = hook.getToLocation().getWorld();
        fromLocation = hook.getFromLocation();
        toLocation = hook.getToLocation();
    }

    public WorldEnterHook(final Player player, final World world, final Location fromLocation,
                          final Location toLocation) {
        this.player = player;
        this.world = world;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
    }

    public Player getPlayer() {
        return player;
    }

    public World getWorld() {
        return world;
    }

    public Location getFromLocation() {
        return fromLocation;
    }

    public Location getToLocation() {
        return toLocation;
    }
}
