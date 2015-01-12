package com.eharrison.canary.playerstate;

import java.util.ArrayList;
import java.util.List;
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

public class PlayerStateManager implements IPlayerStateManager {
	private static final PotionFactory POTION_FACTORY = Canary.factory().getPotionFactory();
	private static final ItemFactory ITEM_FACTORY = Canary.factory().getItemFactory();
	private static final NBTFactory NBT_FACTORY = Canary.factory().getNBTFactory();
	private static final StatisticsFactory STATS_FACTORY = Canary.factory().getStatisticsFactory();
	
	private final PlayerStateConfiguration config;
	
	protected PlayerStateManager(final PlayerStateConfiguration config) {
		this.config = config;
	}
	
	@Override
	public void savePlayerState(final Player player, final String state)
			throws DatabaseWriteException {
		final PlayerDao playerDao = new PlayerDao();
		playerDao.uuid = player.getUUIDString();
		playerDao.state = state;
		
		// playerDao.age = player.getAge();
		// final int fire = player.getFireTicks();
		// final int invunerable = player.getInvulnerabilityTicks();
		// final int level = player.getLevel();
		
		if (config.saveGameMode(state)) {
			playerDao.gameMode = player.getModeId();
		}
		
		if (config.savePrefix(state)) {
			playerDao.prefix = player.getPrefix();
		}
		
		if (config.saveLocations(state)) {
			playerDao.homeLocation = player.getHome().toString();
			playerDao.location = player.getLocation().toString();
			playerDao.spawnLocation = player.getSpawnPosition().toString();
		}
		
		if (config.saveConditions(state)) {
			playerDao.effects = serializePotionEffects(player.getAllActivePotionEffects());
			playerDao.exhaustion = player.getExhaustionLevel();
			playerDao.experience = player.getExperience();
			playerDao.health = player.getHealth();
			playerDao.hunger = player.getHunger();
			playerDao.maxHealth = player.getMaxHealth();
		}
		
		if (config.saveInventory(state)) {
			playerDao.enderInventory = serializeInventory(player.getEnderChestInventory());
			playerDao.inventory = serializeInventory(player.getInventory());
			playerDao.equipment = serializeEquipment(player.getInventory());
		}
		
		if (config.saveAchievements(state)) {
			playerDao.achievements = serializeAchievements(player);
		}
		if (config.saveStatistics(state)) {
			playerDao.statistics = serializeStatistics(player);
		}
		
		playerDao.update();
		
		PlayerStatePlugin.logger.info("Saved " + player.getDisplayName() + " at state " + state);
	}
	
	@Override
	public boolean loadPlayerState(final Player player, final String state)
			throws DatabaseReadException {
		final boolean success = loadPlayerState(player, state, PlayerDao.getPlayerDao(player, state));
		PlayerStatePlugin.logger.info("Loaded " + player.getDisplayName() + " at state " + state + ": "
				+ success);
		return success;
	}
	
	@Override
	public void clearPlayerState(final Player player, final String state) {
		// player.setAge(0);
		
		if (config.saveGameMode(state)) {
			player.setMode(GameMode.SURVIVAL);
		}
		
		if (config.savePrefix(state)) {
			player.setPrefix(null);
		}
		
		if (config.saveLocations(state)) {
			player.setHome(player.getLocation());
			player.setSpawnPosition(player.getLocation());
		}
		
		if (config.saveConditions(state)) {
			player.removeAllPotionEffects();
			player.setExhaustion(0);
			
			// TODO Canary bug
			player.removeExperience(player.getExperience());
			// player.setExperience(0);
			
			player.setHealth(20);
			player.setHunger(20);
			player.setMaxHealth(20);
		}
		
		if (config.saveInventory(state)) {
			// Clear player inventory and equipment
			final PlayerInventory pi = player.getInventory();
			pi.clearContents();
			pi.setBootsSlot(null);
			pi.setChestPlateSlot(null);
			pi.setHelmetSlot(null);
			pi.setLeggingsSlot(null);
			player.getEnderChestInventory().clearContents();
		}
		
		if (config.saveAchievements(state)) {
			for (final Achievements achievements : Achievements.values()) {
				player.setStat(achievements.getInstance(), 0);
			}
		}
		if (config.saveStatistics(state)) {
			for (final Statistics statistics : Statistics.values()) {
				player.setStat(statistics.getInstance(), 0);
			}
		}
		
		PlayerStatePlugin.logger.info("Cleared " + player.getDisplayName() + " state");
	}
	
	private boolean loadPlayerState(final Player player, final String state, final PlayerDao playerDao) {
		boolean loaded = false;
		if (playerDao != null) {
			// player.setAge(playerDao.age);
			// player.setFireTicks(fire);
			// player.setInvulnerabilityTicks(invunerable);
			// player.setLevel(level);
			// player.teleportTo(Location.fromString(playerDao.location));
			
			if (config.saveGameMode(state)) {
				player.setModeId(playerDao.gameMode);
			}
			
			if (config.savePrefix(state)) {
				player.setPrefix(playerDao.prefix);
			}
			
			if (config.saveLocations(state)) {
				player.setHome(Location.fromString(playerDao.homeLocation));
				player.setSpawnPosition(Location.fromString(playerDao.spawnLocation));
			}
			
			if (config.saveConditions(state)) {
				applyPotionEffects(playerDao.effects, player);
				player.setExhaustion(playerDao.exhaustion);
				
				// TODO Canary bug
				player.removeExperience(player.getExperience());
				player.addExperience(playerDao.experience);
				// player.setExperience(playerDao.experience);
				
				player.setHealth(playerDao.health);
				player.setHunger(playerDao.hunger);
				player.setMaxHealth(playerDao.maxHealth);
			}
			
			if (config.saveInventory(state)) {
				restoreInventory(playerDao.enderInventory, player.getEnderChestInventory());
				restoreInventory(playerDao.inventory, player.getInventory());
				restoreEquipment(playerDao.equipment, player.getInventory());
			}
			
			if (config.saveAchievements(state)) {
				restoreAchievements(playerDao.achievements, player);
			}
			if (config.saveStatistics(state)) {
				restoreStatistics(playerDao.statistics, player);
			}
			
			loaded = true;
		}
		return loaded;
	}
	
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
}
