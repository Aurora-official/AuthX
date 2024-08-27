package io.aurora;

public class PlayerData {
    private String name;
    private String key;

    public PlayerData(String name, String key) {
        this.name = name;
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return key;
    }
}
