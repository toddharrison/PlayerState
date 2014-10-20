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

public class PlayerStateManager {
	private static final PotionFactory POTION_FACTORY = Canary.factory().getPotionFactory();
	private static final ItemFactory ITEM_FACTORY = Canary.factory().getItemFactory();
	private static final NBTFactory NBT_FACTORY = Canary.factory().getNBTFactory();
	private static final StatisticsFactory STATS_FACTORY = Canary.factory().getStatisticsFactory();
	
	public void savePlayerState(final Player player, final String state)
			throws DatabaseWriteException {
		final PlayerDao playerDao = new PlayerDao();
		playerDao.uuid = player.getUUIDString();
		playerDao.state = state;
		
		playerDao.age = player.getAge();
		playerDao.effects = serializePotionEffects(player.getAllActivePotionEffects());
		playerDao.exhaustion = player.getExhaustionLevel();
		playerDao.experience = player.getExperience();
		// final int fire = player.getFireTicks();
		playerDao.health = player.getHealth();
		playerDao.homeLocation = player.getHome().toString();
		playerDao.hunger = player.getHunger();
		// final int invunerable = player.getInvulnerabilityTicks();
		// final int level = player.getLevel();
		playerDao.location = player.getLocation().toString();
		playerDao.maxHealth = player.getMaxHealth();
		playerDao.gameMode = player.getModeId();
		playerDao.prefix = player.getPrefix();
		playerDao.spawnLocation = player.getSpawnPosition().toString();
		
		playerDao.enderInventory = serializeInventory(player.getEnderChestInventory());
		playerDao.inventory = serializeInventory(player.getInventory());
		playerDao.equipment = serializeEquipment(player.getInventory());
		
		playerDao.achievements = serializeAchievements(player);
		playerDao.statistics = serializeStatistics(player);
		
		playerDao.update();
	}
	
	public boolean loadPlayerState(final Player player, final String state)
			throws DatabaseReadException {
		boolean loaded = false;
		final PlayerDao playerDao = PlayerDao.getPlayerDao(player, state);
		if (playerDao != null) {
			player.setAge(playerDao.age);
			applyPotionEffects(playerDao.effects, player);
			player.setExhaustion(playerDao.exhaustion);
			player.setExperience(playerDao.experience);
			// player.setFireTicks(fire);
			player.setHealth(playerDao.health);
			player.setHome(Location.fromString(playerDao.homeLocation));
			player.setHunger(playerDao.hunger);
			// player.setInvulnerabilityTicks(invunerable);
			// player.setLevel(level);
			// player.teleportTo(Location.fromString(playerDao.location));
			player.setMaxHealth(playerDao.maxHealth);
			player.setModeId(playerDao.gameMode);
			player.setPrefix(playerDao.prefix);
			player.setSpawnPosition(Location.fromString(playerDao.spawnLocation));
			
			restoreInventory(playerDao.enderInventory, player.getEnderChestInventory());
			restoreInventory(playerDao.inventory, player.getInventory());
			restoreEquipment(playerDao.equipment, player.getInventory());
			
			restoreAchievements(playerDao.achievements, player);
			restoreStatistics(playerDao.statistics, player);
			
			loaded = true;
		}
		return loaded;
	}
	
	public void clearPlayerState(final Player player) {
		// TODO allow configuration of what the starting player state is
		player.setAge(0);
		player.removeAllPotionEffects();
		player.setExhaustion(0);
		player.setExperience(0);
		player.setHealth(20);
		player.setHome(player.getLocation());
		player.setHunger(20);
		player.setMaxHealth(20);
		player.setMode(GameMode.SURVIVAL);
		player.setPrefix(null);
		player.setSpawnPosition(player.getLocation());
		player.getEnderChestInventory().clearContents();
		
		// Clear player inventory and equipment
		final PlayerInventory pi = player.getInventory();
		pi.clearContents();
		pi.setBootsSlot(null);
		pi.setChestPlateSlot(null);
		pi.setHelmetSlot(null);
		pi.setLeggingsSlot(null);
		
		for (final Achievements achievements : Achievements.values()) {
			player.setStat(achievements.getInstance(), 0);
		}
		for (final Statistics statistics : Statistics.values()) {
			player.setStat(statistics.getInstance(), 0);
		}
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
