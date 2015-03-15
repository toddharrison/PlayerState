package com.eharrison.canary.playerstate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.canarymod.Canary;
import net.canarymod.api.GameMode;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.factory.ItemFactory;
import net.canarymod.api.factory.NBTFactory;
import net.canarymod.api.factory.PotionFactory;
import net.canarymod.api.factory.StatisticsFactory;
import net.canarymod.api.inventory.Inventory;
import net.canarymod.api.inventory.Item;
import net.canarymod.api.inventory.ItemType;
import net.canarymod.api.inventory.PlayerInventory;
import net.canarymod.api.nbt.CompoundTag;
import net.canarymod.api.potion.PotionEffect;
import net.canarymod.api.statistics.Achievement;
import net.canarymod.api.statistics.Achievements;
import net.canarymod.api.statistics.Stat;
import net.canarymod.api.statistics.Statistics;
import net.canarymod.api.world.position.Location;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;
import net.visualillusionsent.utils.TaskManager;

import com.eharrison.canary.playerstate.PlayerState.Save;

public class PlayerStateManager {
	private static final PotionFactory POTION_FACTORY = Canary.factory().getPotionFactory();
	private static final ItemFactory ITEM_FACTORY = Canary.factory().getItemFactory();
	private static final NBTFactory NBT_FACTORY = Canary.factory().getNBTFactory();
	private static final StatisticsFactory STATS_FACTORY = Canary.factory().getStatisticsFactory();
	
	private static final long SAVE_DELAY_SECONDS = 10;
	private SavePlayerDaoTask task;
	
	private final PlayerStatePlugin plugin;
	private final Map<String, Map<String, PlayerDao>> states;
	private final Collection<PlayerDao> persistDaos;
	
	public PlayerStateManager(final PlayerStatePlugin plugin) {
		this.plugin = plugin;
		states = new HashMap<String, Map<String, PlayerDao>>();
		persistDaos = new HashSet<PlayerDao>();
	}
	
	public void start() {
		task = new SavePlayerDaoTask();
		TaskManager.scheduleContinuedTaskInSeconds(task, SAVE_DELAY_SECONDS, SAVE_DELAY_SECONDS);
	}
	
	public void stop() {
		TaskManager.removeTask(task);
		synchronized (persistDaos) {
			if (!persistDaos.isEmpty()) {
				for (final PlayerDao playerDao : persistDaos) {
					try {
						playerDao.update();
					} catch (final DatabaseWriteException e) {
						PlayerStatePlugin.LOG.info("Error saving state " + playerDao.state + " for "
								+ playerDao.uuid);
					}
				}
				persistDaos.clear();
			}
		}
	}
	
	public void savePlayerState(final Player player, final String state, final Save[] saves)
			throws DatabaseWriteException {
		Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
		if (playerStateMap == null) {
			playerStateMap = new HashMap<String, PlayerDao>();
			states.put(player.getUUIDString(), playerStateMap);
		}
		PlayerDao playerDao = playerStateMap.get(state);
		if (playerDao == null) {
			playerDao = new PlayerDao();
			playerDao.uuid = player.getUUIDString();
			playerDao.state = state;
			playerStateMap.put(state, playerDao);
		}
		
		synchronized (persistDaos) {
			// playerDao.age = player.getAge();
			// final int fire = player.getFireTicks();
			// final int invunerable = player.getInvulnerabilityTicks();
			// final int level = player.getLevel();
			
			for (final Save save : saves) {
				switch (save) {
					case ACHIEVEMENTS:
						playerDao.achievements = serializeAchievements(player);
						break;
					case CONDITIONS:
						playerDao.effects = serializePotionEffects(player.getAllActivePotionEffects());
						playerDao.exhaustion = player.getExhaustionLevel();
						playerDao.experience = player.getExperience();
						playerDao.health = player.getHealth();
						playerDao.hunger = player.getHunger();
						playerDao.maxHealth = player.getMaxHealth();
						break;
					case GAMEMODE:
						playerDao.gameMode = player.getModeId();
						break;
					case INVENTORY:
						playerDao.enderInventory = serializeInventory(player.getEnderChestInventory());
						playerDao.inventory = serializeInventory(player.getInventory());
						playerDao.equipment = serializeEquipment(player.getInventory());
						break;
					case LOCATIONS:
						playerDao.homeLocation = player.getHome().toString();
						playerDao.spawnLocation = player.getSpawnPosition().toString();
						playerDao.location = player.getLocation().toString();
						break;
					case PREFIX:
						playerDao.prefix = player.getPrefix();
						break;
					case STATISTICS:
						playerDao.statistics = serializeStatistics(player);
						break;
					default:
						throw new UnsupportedOperationException("The specified save is not supported: " + save);
				}
			}
			
			persistDaos.add(playerDao);
		}
		
		PlayerStatePlugin.LOG.info("Saved " + player.getDisplayName() + " at state " + state);
	}
	
	public boolean loadPlayerState(final Player player, final String state, final Save[] saves)
			throws DatabaseReadException {
		boolean success = true;
		Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
		if (playerStateMap == null) {
			playerStateMap = new HashMap<String, PlayerDao>();
			states.put(player.getUUIDString(), playerStateMap);
		}
		PlayerDao playerDao = playerStateMap.get(state);
		if (playerDao == null) {
			playerDao = PlayerDao.getPlayerDao(player, state);
		}
		success = loadPlayerState(player, state, saves, playerDao);
		
		PlayerStatePlugin.LOG.info("Loaded " + player.getDisplayName() + " at state " + state + ": "
				+ success);
		
		if (!success && (!playerStateMap.isEmpty() || !PlayerDao.isNewPlayer(player))) {
			clearPlayerState(player, saves);
		}
		
		return success;
	}
	
	// public Location getPlayerSpawnLocation(final Player player, final String state)
	// throws DatabaseReadException {
	// Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
	// if (playerStateMap == null) {
	// playerStateMap = new HashMap<String, PlayerDao>();
	// states.put(player.getUUIDString(), playerStateMap);
	// }
	// PlayerDao playerDao = playerStateMap.get(state);
	// if (playerDao == null) {
	// playerDao = PlayerDao.getPlayerDao(player, state);
	// }
	//
	// // PlayerStatePlugin.LOG.info(playerDao);
	// return Location.fromString(playerDao.spawnLocation);
	// }
	
	// public void setPlayerSpawnLocation(final Player player, final String state, final Location loc)
	// throws DatabaseReadException {
	// Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
	// if (playerStateMap == null) {
	// playerStateMap = new HashMap<String, PlayerDao>();
	// states.put(player.getUUIDString(), playerStateMap);
	// }
	// PlayerDao playerDao = playerStateMap.get(state);
	// if (playerDao == null) {
	// playerDao = PlayerDao.getPlayerDao(player, state);
	// }
	//
	// synchronized (persistDaos) {
	// // PlayerStatePlugin.LOG.info("playerDAO: " + playerDao);
	// // PlayerStatePlugin.LOG.info("spawnLocation: " + loc);
	// playerDao.spawnLocation = loc.toString();
	// persistDaos.add(playerDao);
	// }
	// }
	
	public Location getPlayerReturnLocation(final Player player, final String state)
			throws DatabaseReadException {
		Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
		if (playerStateMap == null) {
			playerStateMap = new HashMap<String, PlayerDao>();
			states.put(player.getUUIDString(), playerStateMap);
		}
		PlayerDao playerDao = playerStateMap.get(state);
		if (playerDao == null) {
			playerDao = PlayerDao.getPlayerDao(player, state);
		}
		
		return Location.fromString(playerDao.location);
	}
	
	// public int getGameMode(final Player player, final String state) throws DatabaseReadException {
	// Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
	// if (playerStateMap == null) {
	// playerStateMap = new HashMap<String, PlayerDao>();
	// states.put(player.getUUIDString(), playerStateMap);
	// }
	// PlayerDao playerDao = playerStateMap.get(state);
	// if (playerDao == null) {
	// playerDao = PlayerDao.getPlayerDao(player, state);
	// }
	//
	// return playerDao.gameMode;
	// }
	
	// public void restorePlayerLocation(final Player player, final String state)
	// throws DatabaseReadException {
	// Map<String, PlayerDao> playerStateMap = states.get(player.getUUIDString());
	// if (playerStateMap == null) {
	// playerStateMap = new HashMap<String, PlayerDao>();
	// states.put(player.getUUIDString(), playerStateMap);
	// }
	// PlayerDao playerDao = playerStateMap.get(state);
	// if (playerDao == null) {
	// playerDao = PlayerDao.getPlayerDao(player, state);
	// }
	//
	// final Location loc = Location.fromString(playerDao.location);
	// Canary.getServer().addSynchronousTask(new ServerTask(plugin, 0) {
	// @Override
	// public void run() {
	// player.teleportTo(loc);
	// }
	// });
	// }
	
	private boolean loadPlayerState(final Player player, final String state, final Save[] saves,
			final PlayerDao playerDao) {
		boolean loaded = false;
		if (playerDao != null) {
			// player.setAge(playerDao.age);
			// player.setFireTicks(fire);
			// player.setInvulnerabilityTicks(invunerable);
			// player.setLevel(level);
			// player.teleportTo(Location.fromString(playerDao.location));
			
			for (final Save save : saves) {
				switch (save) {
					case ACHIEVEMENTS:
						restoreAchievements(playerDao.achievements, player);
						break;
					case CONDITIONS:
						player.removeAllPotionEffects();
						applyPotionEffects(playerDao.effects, player);
						player.setExhaustion(playerDao.exhaustion);
						player.setExperience(playerDao.experience);
						player.setHealth(playerDao.health);
						player.setHunger(playerDao.hunger);
						player.setMaxHealth(playerDao.maxHealth);
						break;
					case GAMEMODE:
						player.setModeId(playerDao.gameMode);
						break;
					case INVENTORY:
						restoreInventory(playerDao.enderInventory, player.getEnderChestInventory());
						restoreInventory(playerDao.inventory, player.getInventory());
						restoreEquipment(playerDao.equipment, player.getInventory());
						break;
					case LOCATIONS:
						player.setHome(Location.fromString(playerDao.homeLocation));
						player.setSpawnPosition(Location.fromString(playerDao.spawnLocation));
						
						PlayerStatePlugin.LOG.info("TEST: " + player.getSpawnPosition());
						break;
					case PREFIX:
						player.setPrefix(playerDao.prefix);
						break;
					case STATISTICS:
						restoreStatistics(playerDao.statistics, player);
						break;
					default:
						throw new UnsupportedOperationException("The specified save is not supported: " + save);
				}
			}
			loaded = true;
		}
		return loaded;
	}
	
	private void clearPlayerState(final Player player, final Save[] saves)
			throws DatabaseReadException {
		// player.setAge(0);
		
		for (final Save save : saves) {
			switch (save) {
				case ACHIEVEMENTS:
					for (final Achievements achievements : Achievements.values()) {
						player.setStat(achievements.getInstance(), 0);
					}
					break;
				case CONDITIONS:
					player.removeAllPotionEffects();
					player.setExhaustion(0);
					player.setExperience(0);
					player.setHealth(20);
					player.setHunger(20);
					player.setMaxHealth(20);
					break;
				case GAMEMODE:
					player.setMode(GameMode.SURVIVAL);
					break;
				case INVENTORY:
					final PlayerInventory pi = player.getInventory();
					pi.clearContents();
					pi.setBootsSlot(null);
					pi.setChestPlateSlot(null);
					pi.setHelmetSlot(null);
					pi.setLeggingsSlot(null);
					player.getEnderChestInventory().clearContents();
					break;
				case LOCATIONS:
					final Location defaultSpawn = player.getWorld().getSpawnLocation();
					player.setHome(defaultSpawn);
					player.setSpawnPosition(defaultSpawn);
					break;
				case PREFIX:
					player.setPrefix(null);
					break;
				case STATISTICS:
					for (final Statistics statistics : Statistics.values()) {
						player.setStat(statistics.getInstance(), 0);
					}
					break;
				default:
					throw new UnsupportedOperationException("The specified clear is not supported: " + save);
			}
		}
		
		PlayerStatePlugin.LOG.info("Cleared " + player.getDisplayName() + " state");
	}
	
	// public void setPlayerAfterDeathState(final Player player) {
	// // Clear conditions
	// player.removeAllPotionEffects();
	// player.setExhaustion(0);
	// player.setExperience(0);
	// player.setHealth(20);
	// player.setHunger(20);
	// player.setMaxHealth(20);
	//
	// // Clear inventory
	// final PlayerInventory pi = player.getInventory();
	// pi.clearContents();
	// pi.setBootsSlot(null);
	// pi.setChestPlateSlot(null);
	// pi.setHelmetSlot(null);
	// pi.setLeggingsSlot(null);
	// }
	
	private List<String> serializePotionEffects(final List<PotionEffect> effects) {
		final List<String> list = new ArrayList<String>(effects.size());
		for (final PotionEffect effect : effects) {
			final StringBuilder sb = new StringBuilder();
			sb.append(effect.getPotionID());
			sb.append(';');
			sb.append(effect.getDuration());
			sb.append(';');
			sb.append(effect.getAmplifier());
			sb.append(';');
			sb.append(effect.isAmbient());
			list.add(sb.toString());
		}
		return list;
	}
	
	private void applyPotionEffects(final List<String> list, final Player player) {
		for (final String s : list) {
			final StringTokenizer st = new StringTokenizer(s, ";");
			final int id = Integer.parseInt(st.nextToken());
			final int duration = Integer.parseInt(st.nextToken());
			final int amplifier = Integer.parseInt(st.nextToken());
			final boolean ambient = Boolean.parseBoolean(st.nextToken());
			player.addPotionEffect(POTION_FACTORY.newPotionEffect(id, duration, amplifier, ambient));
		}
	}
	
	private List<String> serializeInventory(final Inventory inventory) {
		final List<String> list = new ArrayList<String>();
		final Item[] contents = inventory.getContents();
		for (int i = 0; i < contents.length; i++) {
			final Item item = contents[i];
			if (item != null) {
				final CompoundTag tag = NBT_FACTORY.newCompoundTag(null);
				item.writeToTag(tag);
				tag.put("slot", i);
				list.add(Canary.jsonNBT().baseTagToJSON(tag));
			}
		}
		return list;
	}
	
	private void restoreInventory(final List<String> list, final Inventory inventory) {
		inventory.clearContents();
		for (final String s : list) {
			final CompoundTag tag = (CompoundTag) Canary.jsonNBT().jsonToNBT(s);
			final Item item = ITEM_FACTORY.newItem(ItemType.Tnt);
			item.readFromTag(tag);
			final int slot = tag.getInt("slot");
			item.setSlot(slot);
			inventory.setSlot(item);
		}
	}
	
	private List<String> serializeEquipment(final PlayerInventory inventory) {
		final List<String> list = new ArrayList<String>();
		list.add(jsonify(inventory.getBootsSlot()));
		list.add(jsonify(inventory.getChestplateSlot()));
		list.add(jsonify(inventory.getHelmetSlot()));
		list.add(jsonify(inventory.getLeggingsSlot()));
		return list;
	}
	
	private void restoreEquipment(final List<String> list, final PlayerInventory inventory) {
		assert list.size() == 4;
		inventory.setBootsSlot(dejsonify(list.get(0)));
		inventory.setChestPlateSlot(dejsonify(list.get(1)));
		inventory.setHelmetSlot(dejsonify(list.get(2)));
		inventory.setLeggingsSlot(dejsonify(list.get(3)));
	}
	
	private String serializeAchievements(final Player player) {
		final StringBuilder sb = new StringBuilder();
		for (final Achievements achievements : Achievements.values()) {
			sb.append(achievements.getNativeName());
			sb.append("=");
			sb.append(player.getStat(achievements.getInstance()));
			sb.append(";");
		}
		return sb.toString();
	}
	
	private String serializeStatistics(final Player player) {
		final StringBuilder sb = new StringBuilder();
		for (final Statistics statistics : Statistics.values()) {
			sb.append(statistics.getNativeName());
			sb.append("=");
			sb.append(player.getStat(statistics.getInstance()));
			sb.append(";");
		}
		return sb.toString();
	}
	
	private void restoreAchievements(final String achievements, final Player player) {
		final StringTokenizer st = new StringTokenizer(achievements, ";");
		while (st.hasMoreTokens()) {
			final String t = st.nextToken();
			final int i = t.indexOf("=");
			
			final String achievementName = t.substring(0, i);
			final Achievement a = STATS_FACTORY.getAchievement(achievementName);
			player.setStat(a, Integer.parseInt(t.substring(i + 1)));
		}
	}
	
	private void restoreStatistics(final String statistics, final Player player) {
		final StringTokenizer st = new StringTokenizer(statistics, ";");
		while (st.hasMoreTokens()) {
			final String t = st.nextToken();
			final int i = t.indexOf("=");
			
			final String statName = t.substring(0, i);
			final Stat s = STATS_FACTORY.getStat(statName);
			player.setStat(s, Integer.parseInt(t.substring(i + 1)));
		}
	}
	
	private String jsonify(final Item item) {
		if (item != null) {
			final CompoundTag tag = NBT_FACTORY.newCompoundTag(null);
			item.writeToTag(tag);
			return Canary.jsonNBT().baseTagToJSON(tag);
		} else {
			return null;
		}
	}
	
	private Item dejsonify(final String string) {
		if (string != null && !string.equals("null")) {
			final CompoundTag tag = (CompoundTag) Canary.jsonNBT().jsonToNBT(string);
			final Item item = ITEM_FACTORY.newItem(ItemType.Tnt);
			item.readFromTag(tag);
			return item;
		} else {
			return null;
		}
	}
	
	private class SavePlayerDaoTask implements Runnable {
		@Override
		public void run() {
			synchronized (persistDaos) {
				if (!persistDaos.isEmpty()) {
					for (final PlayerDao playerDao : persistDaos) {
						try {
							playerDao.update();
						} catch (final DatabaseWriteException e) {
							PlayerStatePlugin.LOG.info("Error saving state " + playerDao.state + " for "
									+ playerDao.uuid);
						}
					}
					persistDaos.clear();
				}
			}
		}
	}
}
