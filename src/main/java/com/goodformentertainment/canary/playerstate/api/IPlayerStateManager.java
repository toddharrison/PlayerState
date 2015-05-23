package com.goodformentertainment.canary.playerstate.api;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.world.position.Location;
import net.canarymod.database.exceptions.DatabaseReadException;

public interface IPlayerStateManager {
	/**
	 * The delay in seconds between player state saves.
	 */
	public static final long SAVE_DELAY_SECONDS = 10;
	
	/**
	 * Start the independent save Thread for player states.
	 */
	void startSaveThread();
	
	/**
	 * Stop the independent save Thread for player states.
	 */
	void stopSaveThread();
	
	/**
	 * Schedules the specified saves for the named state of the given Player. The actual save occurs
	 * on a separate Thread at regular intervals.
	 * 
	 * @param player
	 *          The Player to save.
	 * @param state
	 *          The name of the state to save.
	 * @param saves
	 *          The SaveStates to use for this save.
	 */
	void savePlayerState(Player player, String state, SaveState[] saves);
	
	/**
	 * Immediately load the specified saves for named state onto the given Player.
	 * 
	 * @param player
	 *          The Player to load the state onto.
	 * @param state
	 *          The name of the state to load.
	 * @param saves
	 *          The SaveStates to load.
	 * @return True if the load succeeded, false otherwise.
	 * @throws DatabaseReadException
	 *           If there is an error loading from the database.
	 */
	boolean loadPlayerState(Player player, String state, SaveState[] saves)
			throws DatabaseReadException;
	
	/**
	 * Returns the Location the Player should return to for the specified state.
	 * 
	 * @param player
	 *          The Player.
	 * @param state
	 *          The name of the state.
	 * @return The Location the Player was in when the state was saved.
	 * @throws DatabaseReadException
	 *           If there is an error reading from the database.
	 */
	Location getPlayerReturnLocation(Player player, String state) throws DatabaseReadException;
}
