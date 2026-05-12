package com.dragon.integration.listeners;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class JdaEventListenerManager {

    private final JDA jda;

    private final Set<Object> temporaryListeners = ConcurrentHashMap.newKeySet();

    public void registerTemporaryListener(final Object listener) {
        this.registerTemporaryListener(listener, 5, TimeUnit.MINUTES);
    }

    public void registerTemporaryListener(final Object listener, long timeout) {
        this.registerTemporaryListener(listener, timeout, TimeUnit.MINUTES);
    }

    public void registerTemporaryListener(Object listener, long timeout, TimeUnit unit) {
        jda.addEventListener(listener);
        temporaryListeners.add(listener);

        jda.getGatewayPool().schedule(() -> {
            if (temporaryListeners.remove(listener)) {
                jda.removeEventListener(listener);
                log.debug("Auto-removed expired temporary listener");
            }
        }, timeout, unit);
    }

    // Call on shutdown if needed
    @PreDestroy
    public void cleanup() {
        temporaryListeners.forEach(jda::removeEventListener);
        temporaryListeners.clear();
    }
}