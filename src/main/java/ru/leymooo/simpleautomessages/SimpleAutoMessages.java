package ru.leymooo.simpleautomessages;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(id = "simpleautomessages", name = "SimpleAutoMessages", version = "1.3",
        description = "AutoMessages plugin for velocity",
        authors = "Leymooo")
public class SimpleAutoMessages {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final List<AutoMessage> messages;
    private ConfigurationNode config;
    private ScheduledTask task;

    @Inject
    public SimpleAutoMessages(ProxyServer server, Logger logger, @DataDirectory Path userConfigDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = userConfigDirectory;
        this.messages = new ArrayList<>();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        enable(false, true);
        getProxyServer().getCommandManager().register("simpleautomessages", new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (invocation.arguments().length == 0 || !invocation.arguments()[0].equalsIgnoreCase("reload")) {
                    invocation.source().sendMessage(Component.text("Commands: ").append(Component.newline()).append(Component.text("/" + invocation.alias() + " reload")));
                    return;
                }
                if (enable(true, false)) {
                    invocation.source().sendMessage(Component.text("AutoMessages successfully reloaded").color(NamedTextColor.GREEN));
                } else {
                    invocation.source().sendMessage(Component.text("Failed to reload AutoMessages. Check console for more info.").color(NamedTextColor.RED));
                }
            }
            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission("simpleautomessages.reload");
            }
        }, "automessages", "am", "sam" );
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        enable(true, true);
    }

    @Subscribe
    public void onShutDown(ProxyShutdownEvent ev) {
        logger.info("Disabling SimpleAutoMessages");
        disable();
        logger.info("SimpleAutoMessages disabled");
    }

    private boolean enable(boolean reload, boolean delay) {
        logger.info(reload ? "Reloading config..." : "Loading config...");
        ConfigurationNode node = loadConfig();
        if (node == null) {
            logger.error(reload ? "Failed to reload config" : "Failed to load config");
            return false;
        }
        logger.info("Config successfully loaded");
        this.config = node;
        if (reload) {
            disable();
        }
        if (delay) {
            logger.info("AutoMessages enabling will be delayed for 3 sec.");
        }
        createAutoMessages(delay);
        logger.info(reload ? "AutoMessages reloaded" : "AutoMessages loaded");
        return true;
    }

    private void disable() {
        if (task != null) {
            task.cancel();
        }
        messages.forEach(AutoMessage::stopScheduler);
    }


    private void createAutoMessages(boolean delay) {
        Runnable enable = () -> {
            logger.info("Staring AutoMessages tasks");
            messages.clear();
            for (ConfigurationNode node : config.getChildrenMap().values()) {
                String section = (String) node.getKey();
                try {
                    AutoMessage am = AutoMessage.fromConfiguration(node, server);
                    AutoMessage.CheckResult result = am.checkAndRun(
                            server.getPluginManager().fromInstance(SimpleAutoMessages.this).get());
                    switch (result) {
                        case OK:
                            logger.info("'{}' was started", section);
                            messages.add(am);
                            continue;
                        case INTERVAL_NOT_SET:
                            logger.warn("Interval for '{}' is not specified or <=0", section);
                            break;
                        case NO_MESSAGES:
                            logger.warn("Messages for '{}' are not specified or empty", section);
                            break;
                        case NO_SERVERS:
                            logger.warn("Servers for '{}' are not specified or empty", section);
                            break;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to start '{}' {}", section, e);
                    continue;
                }
                logger.warn("'{}' was not started", section);
            }
            this.task = null;
            logger.info("Done");
        };
        if (delay) {
            this.task = server.getScheduler().buildTask(this, enable).delay(3, TimeUnit.SECONDS).schedule();
        } else {
            enable.run();
        }

    }

    private ConfigurationNode loadConfig() {

        File config = new File(dataDirectory.toFile(), "config.yml");
        config.getParentFile().mkdir();
        try {
            if (!config.exists()) {
                try (InputStream in = SimpleAutoMessages.class.getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, config.toPath());
                }
            }
            YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setFile(config).setIndent(2).build();
            return loader.load();
        } catch (Exception ex) {
            logger.error("Could not load or save config", ex);
        }
        return null;
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getProxyServer() {
        return server;
    }

    public ConfigurationNode getConfig() {
        return config;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }
}
