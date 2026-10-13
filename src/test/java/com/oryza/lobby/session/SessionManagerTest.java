package com.oryza.lobby.session;

import com.oryza.lobby.i18n.MessageBundle;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionManagerTest {
    @Test
    void testSessionLifecycleState() {
        MessageBundle bundle = new MessageBundle("pt_br", null);
        assertNotNull(bundle);

        String msg = bundle.get("pt_br", "lobby.panel.status.offline", Map.of());
        assertEquals("Este servidor está desligado.", msg);

        String msgEn = bundle.get("en_us", "lobby.panel.status.offline", Map.of());
        assertEquals("This server is offline.", msgEn);

        String msgStarting = bundle.get("pt_br", "lobby.panel.status.starting", Map.of());
        assertEquals("O servidor já está ligando...", msgStarting);
    }
}
