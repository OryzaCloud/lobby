package com.oryza.lobby;

import com.oryza.lobby.app.LobbyApp;
import com.oryza.lobby.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            Config config = Config.load();
            LobbyApp app = new LobbyApp(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Lobby Service...");
                app.stop();
            }));

            app.start();
        } catch (Exception e) {
            log.error("Fatal startup error", e);
            System.exit(1);
        }
    }
}
