package ru.leymooo.simpleautomessages;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
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

@Plugin(id = "simpleautomessages", name = "SimpleAutoMessages", version = "1.2",
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
        logger.info("Loading config");
        if (!loadConfig() || config == null) {
            logger.error("Config is not loaded. Plugin will be inactive");
            return;
        }
        logger.info("Config loaded");
        logger.info("Plugin will be enabled after 3 seconds");
        createAutoMessages();
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        logger.info("Reloading");
        if (!loadConfig() || config == null) {
            logger.error("Can not reload config");
            return;
        }
        messages.forEach(AutoMessage::stopScheduler);
        logger.info("Plugin will be reloaded after 3 seconds");
        if (task != null) {
            task.cancel();
        }
        createAutoMessages();
    }

    @Subscribe
    public void onShutDown(ProxyShutdownEvent ev) {
        logger.info("Disabling SimpleAutoMessages");
        messages.forEach(AutoMessage::stopScheduler);
        logger.info("SimpleAutoMessages disabled");
    }

    private void createAutoMessages() {
        this.task = server.getScheduler().buildTask(this, () -> {
            logger.info("Staring AutoMessages tasks");
            synchronized (messages) {
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
                                logger.warn("Messages for '{}' is not specified or empty", section);
                                break;
                            case NO_SERVERS:
                                logger.warn("Servers for '{}' is not specified or empty", section);
                                break;
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to start '{}' {}", section, e);
                        continue;
                    }
                    logger.warn("'{}' was not started", section);
                }
                logger.info("Done");
            }
        }).delay(3, TimeUnit.SECONDS).schedule();
    }

    private boolean loadConfig() {

        File config = new File(dataDirectory.toFile(), "config.yml");
        config.getParentFile().mkdir();
        try {
            if (!config.exists()) {
                try (InputStream in = SimpleAutoMessages.class.getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, config.toPath());
                }
            }
            YAMLConfigurationLoader loader = YAMLConfigurationLoader.builder().setFile(config).setIndent(2).build();
            this.config = loader.load();
            return true;
        } catch (Exception ex) {
            logger.error("Could not load or save config", ex);
        }
        return false;
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
