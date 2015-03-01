PlayerState
===========
v0.2.0

PlayerState is a simple plugin which will save and load player state within Minecraft. For instance, a server with a survival and creative world using PlayerState can prevent players from carrying items from creative into the survival world. It is built against the latest 1.2 snapshot of [canarymod](http://www.canarymod.net/) and supports Minecraft 1.8.

## Features

This plugin can save information like:

* Conditions like health, hunger, experience, etc.
* Effects from potions
* Player gamemode
* Spawn and home locations
* Inventories in Ender chest and player inventory
* Equipment
* Statistics
* Achievements
* And more...

## Configuration

### Exact Spawn

    exactSpawn=true
The configuration property `exactSpawn` is a boolean and defaults to `true`. This property controls how PlayerState manages the spawn location for players. If set to `true` then players will spawn to the exact world spawn coordinate when using the command `\spawn`. On death they will also return to the exact world spawn coordinate unless they have a bed spawn location set. If set to `false` then default Minecraft behavior will occur, with the player spawing at the highest location at world spawn using `\spawn` and spawning in nearby the world spawn on death.

### Global State

    state.global=WORLD_ALL
The configuration property `state.global` is a string and defaults to "WORLD_ALL". This property controls what state all worlds on the server will default to unless specified otherwise. This means that unless a world is configured otherwise, it will share all state with other worlds on the server. The "WORLD_ALL" is the state string value that will appear in the PlayerState database.

### World States

    state.world.<world_name>=<state>
There is one possible configuration for each world on the server. Using the name of the world and the name of the state can create combinations of worlds that share state. Say, for example, that you have three worlds. Two are survival and one is creative. You want to share the state on the survival worlds but not on creative. Your configuration of states could look like this:

    state.global=WORLD_ALL
    state.world.creative=fun
This would cause the two survival worlds to share state implicitly through the "WORLD_ALL" state while separating out the creative (name of the world) world using the "fun" state.

### Player Property Saves

    save.<state>.achievements=false
The save configuration properties manage what specific player properties to save for each state. Usually the defaults will be good enough for most servers, but these properties allow you to configure things differently if you have other needs. Say, for example, that you wanted to have a hardcore survival world that kept track of player achievements and statistics separately from your main survival world. You could configure that using these properties:

    save.hardcore.achievements=true
    save.hardcore.statistics=true

If none are specified then the defaults are:

    save.<state>.achievements=false
    save.<state>.statistics=false
    save.<state>.prefix=false
    save.<state>.inventory=true
    save.<state>.gamemode=true
    save.<state>.locations=true
    save.<state>.conditions=true

## Commands

You can use the following commands to save player states manually. This will default to using the player issuing the command as the target for the state save. The commands look like this (with `/ps` as a shortened form):

    /playerstate save <state> [player]
    /playerstate load <state> [player]

For example, let's say I want to save my current state under the state name "foo", which I could use to load it again later.

    /ps save foo

I would then load my previous state again anytime using this command:

    /ps load foo
The PlayerState load command will also restore the player to the location where the save was made.

## Permissions

You can control access using these permissions:

* `playerstate.command.save` to save states
* `playerstate.command.load` to load states

## Plugin API

### Managed Worlds

You may register a world to be managed using the PlayerState API. This is how other plugins would leverage the PlayerState functionality. A registered world will have it's state saved automatically for you independently of other states. When you register the world you can specify what properties of the user will be saved and loaded for that world. Use the following commands to register through the API:

* `PlayerState.registerWorld(World world, Save[] saves);` to register a world
* `PlayerState.unregisterWorld(World world);` to unregister a world

Worlds managed through the PlayerState API will automatically be assigned their own state, of the pattern "WORLD_MANAGED_<world_name>", and will use the saves to determine which player properties will be persisted.

### World Hooks

PlayerState offers new hooks that can be used to determine when a player enters and exits a world. These are called the `WorldEnterHook` and `WorldExitHook`. These hooks are useful when there are things you need to do when a player enters and exits from a world, either through death, command, or login/out.

## Build Dependency

The maven dependency looks like this, but I don't have a hosting repository just yet:

    <dependency>
      <groupId>com.eharrison.canary</groupId>
      <artifactId>player-state</artifactId>
      <version>0.2.0</version>
      <scope>provided</scope>
    </dependency>
