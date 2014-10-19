PlayerState
===========

PlayerState is a [canarymod](http://www.canarymod.net/) 1.1.3-SNAPSHOT plugin designed to save and load player state. This includes:

* Conditions like health, hunger, experience, etc.
* Effects from potions
* Spawn, home, and current locations
* Inventories in Ender chest and player inventory
* Equipment
* Prefix
* And more...

PlayerState will also automatically save and load the state of a player as they transition between worlds if __automate__ is set to __true__ in the PlayerState.cfg. The data for all player states is stored in the canary _playerstate_player_ table.

You can control access using these permissions:

* _playerstate.command.save_ to save states
* _playerstate.command.load_ to load states

The commands look like this (with _/ps_ as a shortened form):

* _/playerstate &lt;save&gt; &lt;name&gt; [player]_
* _/playerstate &lt;load&gt; &lt;name&gt; [player]_
