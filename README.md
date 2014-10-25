PlayerState
===========
v0.0.6

PlayerState is a simple plugin which will save and load player state within Minecraft. It is built against the latest 1.1.3-SNAPSHOT of [canarymod](http://www.canarymod.net/) and I will be maintaining it and transitioning to Minecraft 1.8 when that is ready. I will also be adding additional features as they come to mind. This plugin will save information like:

* Conditions like health, hunger, experience, etc.
* Effects from potions
* Spawn and home locations
* Inventories in Ender chest and player inventory
* Equipment
* Statistics
* Achievements
* And more...

PlayerState will automatically save and load the state of a player as they transition between worlds if __automate__ is set to __true__ in the PlayerState.cfg (this will be automatically populated in the future). This will save the state automatically when the player leaves a world and restore it on their return. The data for all player states is stored in the canary _playerstate_player_ table.

In addition to or instead of automatic, you could use commands to save player states. This will default to using the player issuing the command as the target for the state save. The commands look like this (with _/ps_ as a shortened form):

* _/playerstate &lt;save&gt; &lt;name&gt; [player]_
* _/playerstate &lt;load&gt; &lt;name&gt; [player]_

This would save my current state under the name "foo", which I could use to load it again later.
* /ps save foo

You can control access using these permissions:

* _playerstate.command.save_ to save states
* _playerstate.command.load_ to load states

If automate is not enabled, you may register a world using the PlayerState API. A registered world will have it's state saved automatically for you just as if automate were enabled. All unregistered worlds will share one state among themselves. Use the following commands to register through the API:

* _PlayerState.registerWorld(world);_ to register a world
* _PlayerState.unregisterWorld(world);_ to unregister a world

PlayerState offers new hooks that can be used to determine when a player enters and exits a world. These are called the _WorldEnterHook_ and _WorldExitHook_. These hooks are useful when there is additional work that your plugin needs to perform in conjunction with saving the state of the players. Detailed examples will be provided in the future.

