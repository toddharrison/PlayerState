package com.goodformentertainment.canary.playerstate.api;

public enum SaveState {
    ACHIEVEMENTS("achievements"), STATISTICS("statistics"), INVENTORY("inventory"), GAMEMODE(
            "gamemode"), PREFIX("prefix"), LOCATIONS("locations"), CONDITIONS("conditions");

    private final String name;

    SaveState(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
