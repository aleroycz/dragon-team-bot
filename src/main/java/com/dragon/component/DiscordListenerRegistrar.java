package com.dragon.component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.dragon.integration.DiscordEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordListenerRegistrar {

    private final JDA jda;
    private final List<ListenerAdapter> listenerAdapters;

    @PostConstruct
    public void registerListeners() {
        if (listenerAdapters.isEmpty()) {
            log.warn("No Discord event listeners found!");
            return;
        }

        log.info("Registering {} Discord event listeners", listenerAdapters.size());

        List<ListenerAdapter> discordListeners = listenerAdapters.stream()
                .filter(l -> l instanceof DiscordEventListener)
                .toList();

        if (discordListeners.isEmpty()) {
            log.warn("No Discord-specific listeners found (check marker interface)");
        }

        jda.addEventListener(discordListeners.toArray());

        log.info("Successfully registered Discord listeners: {}",
                discordListeners.stream()
                        .map(l -> l.getClass().getSimpleName())
                        .collect(Collectors.joining(", ")));
    }
}