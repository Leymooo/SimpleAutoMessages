package ru.leymooo.simpleautomessages;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.config.Configuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoMessage {

    private static final Pattern RANGE_MATCH = Pattern.compile("(.+)\\{(\\d+)-(\\d+)\\}");

    private final Random random = new Random();

    private final ProxyServer proxyServer;
    private final List<RegisteredServer> servers = new ArrayList<>();
    private final List<MessageContainer> messages = new ArrayList<>();
    private final int interval;
    private final boolean shuffle;
    private boolean global;

    private ScheduledTask task;
    private int index = -1;

    public AutoMessage(ProxyServer server, List<String> servers, int interval, boolean shuffle, List<String> messages) {
        this.proxyServer = server;
        this.interval = interval;
        this.shuffle = shuffle;
        messages.forEach(message -> this.messages.add(new MessageContainer(message)));
        parseServers(servers);
    }

    public static AutoMessage fromConfiguration(Configuration configuration, ProxyServer server) {
        return new AutoMessage(server,
                configuration.getStringList("servers"),
                configuration.getInt("interval", 0),
                configuration.getBoolean("random", false),
                configuration.getStringList("messages"));
    }

    private void parseServers(List<String> servers) {
        servers.stream().filter("global"::equalsIgnoreCase).findAny().ifPresent(s -> this.global = true);
        if (!global) {
            servers.forEach(server -> {
                Matcher matcher = RANGE_MATCH.matcher(server);
                if (matcher.find()) {
                    String name = matcher.group(1);
                    int lower = Integer.parseInt(matcher.group(2));
                    int upper = Integer.parseInt(matcher.group(3));
                    for (int i = lower; i <= upper; i++) {
                        proxyServer.getServer(name + i).ifPresent(this.servers::add);
                    }
                } else {
                    proxyServer.getServer(server).ifPresent(this.servers::add);
                }
            });
        }
    }

    public CheckResult checkAndRun(PluginContainer plugin) {
        if (messages.isEmpty()) {
            return CheckResult.NO_MESSAGES;
        }
        if (!global && servers.isEmpty()) {
            return CheckResult.NO_SERVERS;
        }
        if (interval <= 0) {
            return CheckResult.INTERVAL_NOT_SET;
        }
        if (task != null) {
            return CheckResult.ALREADY_RUNNING;
        }
        this.task = proxyServer.getScheduler().buildTask(plugin, () -> {
            checkIndex();
            MessageContainer message = messages.get(index);
            if (global) {
                if (!message.hasPlaceholders()) {
                    proxyServer.sendMessage(message.getForPlayer(null));
                } else {
                    proxyServer.getAllPlayers().forEach(pl -> pl.sendMessage(message.getForPlayer(pl)));
                }
            } else {
                this.servers.forEach(srv -> srv.getPlayersConnected().forEach(pl -> pl.sendMessage(message.getForPlayer(pl))));
            }
            index++;
        }).repeat(interval, TimeUnit.SECONDS).schedule();
        return CheckResult.OK;
    }

    public void stopScheduler() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void checkIndex() {
        if (index == -1 || index >= messages.size()) {
            index = 0;
            if (shuffle) {
                Collections.shuffle(messages, random);
            }
        }
    }

    public enum CheckResult {
        OK,
        NO_SERVERS,
        NO_MESSAGES,
        INTERVAL_NOT_SET,
        ALREADY_RUNNING
    }

    private static class MessageContainer {

        private final static JsonParser JSON_PARSER = new JsonParser();

        private final String message;
        private final Component preCreated;
        private final boolean isJson;

        public MessageContainer(String message) {
            this.message = message;
            JsonElement parsed = null;
            try {
                parsed = JSON_PARSER.parse(message);
            } catch (JsonSyntaxException ignored) {
            }
            isJson = parsed != null && !parsed.isJsonPrimitive();
            preCreated = (message.contains("%player%") || message.contains("%server%")) ? null : create(message);
        }

        public boolean hasPlaceholders() {
            return preCreated == null;
        }

        public Component getForPlayer(Player player) {
            if (!hasPlaceholders()) {
                return preCreated;
            }
            Optional<ServerConnection> server = player.getCurrentServer();
            String s_server = server.isPresent() ? server.get().getServerInfo().getName() : "<none>";
            return create(message.replace("%player%", player.getUsername()).replace("%server%", s_server));
        }

        private Component create(String text) {
            return isJson ? GsonComponentSerializer.gson().deserialize(text) : LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        }

    }
}
