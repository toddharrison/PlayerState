package com.goodformentertainment.canary.playerstate.api;

import net.canarymod.api.world.World;

public interface IWorldStateManager {
    String ALL_WORLDS = "WORLD_ALL";
    String MANAGED_WORLD = "WORLD_MANAGED_";

    void registerWorld(World world, final SaveState[] saves);

    void unregisterWorld(World world);

    String getManagedWorldState(World world);

    SaveState[] getManagedStateSaves(String state);
}
