package com.eharrison.canary.playerstate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.database.Column;
import net.canarymod.database.Column.DataType;
import net.canarymod.database.DataAccess;
import net.canarymod.database.Database;
import net.canarymod.database.exceptions.DatabaseReadException;
import net.canarymod.database.exceptions.DatabaseWriteException;

public class PlayerDao extends DataAccess {
	public static final String UUID = "uuid";
	public static final String STATE = "state";
	public static final String AGE = "age";
	public static final String EFFECTS = "effects";
	public static final String EXHAUSTION = "exhaustion";
	public static final String EXPERIENCE = "experience";
	public static final String HEALTH = "health";
	public static final String HOME_LOCATION = "home_location";
	public static final String HUNGER = "hunger";
	public static final String LOCATION = "location";
	public static final String MAX_HEALTH = "max_health";
	public static final String GAME_MODE = "game_mode";
	public static final String PREFIX = "prefix";
	public static final String SPAWN_LOCATION = "spawn_location";
	public static final String ENDER_INVENTORY = "ender_inventory";
	public static final String INVENTORY = "inventory";
	public static final String EQUIPMENT = "equipment";
	
	public static PlayerDao getPlayerDao(final Player player, final String state)
			throws DatabaseReadException {
		final PlayerDao playerDao = new PlayerDao();
		final Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(PlayerDao.UUID, player.getUUIDString());
		filters.put(PlayerDao.STATE, state);
		Database.get().load(playerDao, filters);
		
		if (playerDao.hasData()) {
			return playerDao;
		} else {
			return null;
		}
	}
	
	public PlayerDao() {
		super("playerstate_player");
	}
	
	@Override
	public PlayerDao getInstance() {
		return new PlayerDao();
	}
	
	@Column(columnName = UUID, dataType = DataType.STRING)
	public String uuid;
	
	@Column(columnName = STATE, dataType = DataType.STRING)
	public String state;
	
	@Column(columnName = AGE, dataType = DataType.INTEGER)
	public int age;
	
	@Column(columnName = EFFECTS, dataType = DataType.STRING, isList = true)
	public List<String> effects;
	
	@Column(columnName = EXHAUSTION, dataType = DataType.FLOAT)
	public float exhaustion;
	
	@Column(columnName = EXPERIENCE, dataType = DataType.INTEGER)
	public int experience;
	
	@Column(columnName = HEALTH, dataType = DataType.FLOAT)
	public float health;
	
	@Column(columnName = HOME_LOCATION, dataType = DataType.STRING)
	public String homeLocation;
	
	@Column(columnName = HUNGER, dataType = DataType.INTEGER)
	public int hunger;
	
	@Column(columnName = LOCATION, dataType = DataType.STRING)
	public String location;
	
	@Column(columnName = MAX_HEALTH, dataType = DataType.DOUBLE)
	public double maxHealth;
	
	@Column(columnName = GAME_MODE, dataType = DataType.INTEGER)
	public int gameMode;
	
	@Column(columnName = PREFIX, dataType = DataType.STRING)
	public String prefix;
	
	@Column(columnName = SPAWN_LOCATION, dataType = DataType.STRING)
	public String spawnLocation;
	
	@Column(columnName = ENDER_INVENTORY, dataType = DataType.STRING, isList = true)
	public List<String> enderInventory;
	
	@Column(columnName = INVENTORY, dataType = DataType.STRING, isList = true)
	public List<String> inventory;
	
	@Column(columnName = EQUIPMENT, dataType = DataType.STRING, isList = true)
	public List<String> equipment;
	
	public void update() throws DatabaseWriteException {
		final Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(PlayerDao.UUID, uuid);
		filters.put(PlayerDao.STATE, state);
		Database.get().update(this, filters);
	}
}
