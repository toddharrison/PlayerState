package com.eharrison.canary.playerstate;

import net.canarymod.Canary;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.chat.MessageReceiver;
import net.canarymod.commandsys.Command;
import net.canarymod.commandsys.CommandListener;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;

public class PlayerStateCommand implements CommandListener {
	private final PlayerStateManager manager;
	
	public PlayerStateCommand(final PlayerStateManager manager) {
		this.manager = manager;
	}
	
	@Command(aliases = {
			"playerstate", "ps"
	}, description = "Manage player states", permissions = {
		"playerstate.command"
	}, toolTip = "/playerstate")
	public void playerStateCommand(final MessageReceiver caller, final String[] parameters) {
		
	}
	
	@Command(aliases = {
		"save"
	}, parent = "playerstate", description = "Save player state", permissions = {
		"playerstate.command.save"
	}, toolTip = "/playerstate <save> <name> [player]", min = 2, max = 3)
	public void saveCommand(final MessageReceiver caller, final String[] parameters)
			throws DatabaseWriteException {
		Player player = null;
		if (parameters.length > 2) {
			player = Canary.getServer().matchPlayer(parameters[2]);
		} else if (caller instanceof Player) {
			player = (Player) caller;
		}
		if (player != null) {
			final String state = parameters[1];
			manager.savePlayerState(player, state);
			caller.message("Saved " + player.getDisplayName() + " current state as " + state);
		}
	}
	
	@Command(aliases = {
		"load"
	}, parent = "playerstate", description = "Load player state", permissions = {
		"playerstate.command.restore"
	}, toolTip = "/playerstate <load> <name> [player]", min = 2, max = 3)
	public void changeCommand(final MessageReceiver caller, final String[] parameters)
			throws DatabaseReadException {
		Player player = null;
		if (parameters.length > 2) {
			player = Canary.getServer().matchPlayer(parameters[2]);
		} else if (caller instanceof Player) {
			player = (Player) caller;
		}
		if (player != null) {
			final String state = parameters[1];
			manager.changePlayerState(player, state);
			caller.message("Changed " + player.getDisplayName() + " current state to " + state);
		}
	}
}
