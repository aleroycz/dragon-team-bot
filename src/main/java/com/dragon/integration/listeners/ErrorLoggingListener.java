package com.dragon.integration.listeners;

import io.sentry.Sentry;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.dragon.integration.DiscordEventListener;
import org.springframework.stereotype.Component;

@Component
public class ErrorLoggingListener extends ListenerAdapter implements DiscordEventListener {

    @Override
    public void onException(ExceptionEvent event) {
        Sentry.logger().error("JDA encountered an exception", event.getCause());
    }
}
