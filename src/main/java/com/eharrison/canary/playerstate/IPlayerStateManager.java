package com.eharrison.canary.playerstate;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;

public interface IPlayerStateManager {
	void savePlayerState(Player player, String state) throws DatabaseWriteException;
	
	boolean loadPlayerState(Player player, PlayerDao playerDao);
	
	boolean loadPlayerState(Player player, String state) throws DatabaseReadException;
	
	void clearPlayerState(Player player);
}
