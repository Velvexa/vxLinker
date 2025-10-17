package com.velvexa.vxlinker.storage;

import java.util.UUID;


public interface StorageProvider {


    void setLinkedAccount(UUID uuid, String playerName, String discordId);


    String getDiscordId(UUID uuid);


    UUID getPlayerUUID(String discordId);

 
    void removeLinkedAccount(UUID uuid);


    boolean isPlayerLinked(UUID uuid);

 
    boolean isDiscordLinked(String discordId);


    void close();
}
