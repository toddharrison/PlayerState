package com.eharrison.canary.playerstate;

public final class PlayerState {
	protected static IPlayerStateManager playerStateManager;
	
	public static IPlayerStateManager getPlayerStateManager() {
		return playerStateManager;
	}
}
