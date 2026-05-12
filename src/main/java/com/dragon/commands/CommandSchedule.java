package com.dragon.commands;

import com.dragon.dto.event.EventStatus;
import com.dragon.entity.event.Event;
import com.dragon.integration.SlashCommand;
import com.dragon.repository.event.EventRepository;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CommandSchedule implements SlashCommand {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm 'GMT+2'");

    // Valorant Premier official assets
    private static final String ICON_PLAYOFFS = "https://media.valorant-api.com/gamemodes/96bd3920-4f36-d026-2b28-c683eb0bcac5/displayicon.png";
    private static final String ICON_PRACTICE = "https://media.valorant-api.com/gamemodes/a8790ec5-4237-f2f0-e93b-08a8e89865b2/displayicon.png";
    private static final String ICON_MATCH    = "https://media.valorant-api.com/gamemodes/96bd3920-4f36-d026-2b28-c683eb0bcac5/listviewicontalllocked.png";
    private static final String BANNER_URL    = "https://media.valorant-api.com/gamemodes/96bd3920-4f36-d026-2b28-c683eb0bcac5/displayicon.png";

    private final EventRepository eventRepository;

    @Override
    public String getName() {
        return "schedule";
    }

    @Override
    public String getDescription() {
        return "Display the next 10 upcoming Premier events.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Event> events = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.SCHEDULED)
                .filter(e -> e.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .sorted(Comparator.comparing(Event::getStartTime))
                .limit(5)
                .toList();

        if (events.isEmpty()) {
            event.replyComponents(Container.of(
                    Section.of(
                            Thumbnail.fromUrl(CommandAbout.LOGO_URL),
                            TextDisplay.of("# Premier Schedule"),
                            TextDisplay.of("No upcoming events found.")
                    )
            )).useComponentsV2().setEphemeral(true).queue();
            return;
        }

        List<ContainerChildComponent> items = new ArrayList<>();

        items.add(Section.of(
                Thumbnail.fromUrl(BANNER_URL),
                TextDisplay.of("# Premier Schedule"),
                TextDisplay.of("Next **" + events.size() + "** upcoming events.")
        ));

        for (Event e : events) {
            boolean isPlayoffs = e.getEventName().contains("Playoffs");
            boolean isPractice = e.getEventName().contains("Practice");

            String iconUrl = isPlayoffs ? ICON_PLAYOFFS
                    : isPractice ? ICON_PRACTICE
                    : ICON_MATCH;

            LocalDateTime startGmt2 = e.getStartTime().plusHours(2);
            LocalDateTime endGmt2   = e.getEndTime().plusHours(2);

            items.add(Separator.createDivider(Separator.Spacing.SMALL));
            items.add(Section.of(
                    Thumbnail.fromUrl(iconUrl),
                    TextDisplay.of("**" + e.getEventName() + "**"),
                    TextDisplay.of(e.getEventDescription()),
                    TextDisplay.of("-# 🕐 " + startGmt2.format(FMT) + " → " + endGmt2.format(FMT))
            ));
        }

        items.add(Separator.createDivider(Separator.Spacing.SMALL));
        items.add(ActionRow.of(
                Button.link("https://playvalorant.com/en-gb/premier/", "Premier Hub")
        ));

        event.replyComponents(Container.of(items))
                .useComponentsV2()
                .queue();
    }
}