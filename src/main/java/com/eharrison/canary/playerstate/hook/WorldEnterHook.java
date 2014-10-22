package com.eharrison.canary.playerstate.hook;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.World;
import net.canarymod.hook.Hook;

public class WorldEnterHook extends Hook {
	private final Player player;
	private final World world;
	
	public WorldEnterHook(final Player player, final World world) {
		this.player = player;
		this.world = world;
	}
	
	public Player getPlayer() {
		return player;
	}
	
	public World getWorld() {
		return world;
	}
}
