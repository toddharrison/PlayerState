PlayerState
===========
v0.0.7

PlayerState is a simple plugin which will save and load player state within Minecraft. For instance, a server with a survival and creative world using PlayerState can prevent players from carrying items from creative into the survival world. It is built against the latest 1.2.0-SNAPSHOT of [canarymod](http://www.canarymod.net/) and supports Minecraft 1.8. I will be adding additional features as they come to mind. This plugin saves information like:

* Conditions like health, hunger, experience, etc.
* Effects from potions
* Player gamemode
* Spawn and home locations
* Inventories in Ender chest and player inventory
* Equipment
* Statistics
* Achievements
* And more...

PlayerState is not very configurable at the moment, I will be adding more fine-grained control as I need it. PlayerState will automatically save and load the state of a player as they transition between worlds if __automate__ is set to __true__ in the PlayerState.cfg (this will be automatically populated in the future). This will save the state automatically when the player leaves a world and restore it on their return, say when they use a spawn to a different world. Their state will not change transitioning to the nether, end, or back within the same world. The data for all player states is stored in the canary _playerstate_player_ table.

In addition to or instead of automatic, you could use commands to save player states. This will default to using the player issuing the command as the target for the state save. The commands look like this (with _/ps_ as a shortened form):

* _/playerstate &lt;save&gt; &lt;name&gt; [player]_
* _/playerstate &lt;load&gt; &lt;name&gt; [player]_

This would save my current state under the name "foo", which I could use to load it again later.
* /ps save foo

You can control access using these permissions:

* _playerstate.command.save_ to save states
* _playerstate.command.load_ to load states

If automate is not enabled, you may register a world using the PlayerState API. A registered world will have it's state saved automatically for you just as if automate were enabled. All unregistered worlds will share one state among themselves. If automate is enabled, they will each be separate. Use the following commands to register through the API:

* _PlayerState.registerWorld(world);_ to register a world
* _PlayerState.unregisterWorld(world);_ to unregister a world

PlayerState offers new hooks that can be used to determine when a player enters and exits a world. These are called the _WorldEnterHook_ and _WorldExitHook_. These hooks are useful when there is additional work that your plugin needs to perform in conjunction with saving the state of the players. Detailed examples will be provided in the future.

The maven dependency looks like this, but I don't have a hosting repository just yet:
>
    <dependency>
      <groupId>com.eharrison.canary</groupId>
      <artifactId>player-state</artifactId>
      <version>0.0.7</version>
      <scope>provided</scope>
    </dependency>

