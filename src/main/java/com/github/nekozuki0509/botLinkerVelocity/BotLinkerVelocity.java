package com.github.nekozuki0509.botLinkerVelocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

@Plugin(
        id = "botlinkervelocity",
        name = "BotLinkerVelocity",
        version = "1.0.0"
)
public class BotLinkerVelocity {

    private static final String BOT_HINT_MARKER = "_botlinker_";

    private final ProxyServer proxy;

    private final Logger logger;

    @Inject
    public BotLinkerVelocity(final Logger logger, final ProxyServer proxy) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getEventManager().register(this, PreLoginEvent.class, e -> {
            String host = e.getConnection().getVirtualHost()
                    .map(InetSocketAddress::getHostString)
                    .orElse("");

            if (host.contains(BOT_HINT_MARKER)) e.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        });
    }

    @Subscribe
    public EventTask onGameProfileRequest(GameProfileRequestEvent event) {
        String host = event.getConnection().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");

        if (!host.contains(BOT_HINT_MARKER)) return null;

        return EventTask.withContinuation(continuation -> {
            try {
                String url = "https://playerdb.co/api/player/minecraft/" + event.getGameProfile().getName();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> resp;
                try {
                    resp = client.send(
                            HttpRequest.newBuilder(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.get("code").getAsString().equals("player.found")) {
                    JsonObject player = json.getAsJsonObject("data").getAsJsonObject("player");
                    JsonObject properties = player.getAsJsonArray("properties").get(0).getAsJsonObject();
                    event.setGameProfile(new GameProfile(
                            UUID.fromString(player.get("id").getAsString()),
                            player.get("username").getAsString(),
                            List.of(new GameProfile.Property(properties.get("name").getAsString(), properties.get("value").getAsString(), properties.get("signature").getAsString()))
                    ));
                    logger.info("Bot verified and profile updated for: {}", player.get("username").getAsString());
                } else logger.warn("Player not found in PlayerDB for {}", event.getGameProfile().getName());
            } catch (Exception e) {
                logger.warn("skin fetch failed for {}", event.getGameProfile().getId(), e);
            } finally {
                continuation.resume();
            }
        });
    }
}
