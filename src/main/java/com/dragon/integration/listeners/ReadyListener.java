package com.dragon.integration.listeners;

import io.sentry.Sentry;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.dragon.integration.DiscordEventListener;
import org.springframework.stereotype.Component;

@Component
public class ReadyListener extends ListenerAdapter implements DiscordEventListener {

    @Override
    public void onReady(ReadyEvent event) {
        Sentry.logger().info("Discord bot is ready! Logged in as {}",
                event.getJDA().getSelfUser().getAsTag());
    }
}
