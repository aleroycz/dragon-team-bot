package com.dragon.commands.moderation;

import com.dragon.entity.ModeratorNote;
import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.moderation.ModeratorNoteService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandNotes implements SlashCommand {

    private final ModeratorNoteService noteService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "notes"; }

    @Override
    public String getDescription() { return "View private staff notes on a member."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "The member to look up", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.MODERATION, event)) return;

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to view moderator notes.").build()
            ).setEphemeral(true).queue();
            return;
        }

        Member target = Objects.requireNonNull(event.getOption("member")).getAsMember();
        if (target == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Member Not Found",
                    "Could not resolve the target member. Are they still in the server?").build()
            ).setEphemeral(true).queue();
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<ModeratorNote> notes = noteService.getNotes(target.getId(), guildId);

        if (notes.isEmpty()) {
            event.replyEmbeds(
                    embed.info(IconRegistry.ICON_BOOK,
                                    "📝 Notes — " + target.getUser().getAsTag(),
                                    "No notes have been added for this member.")
                            .setThumbnail(target.getEffectiveAvatarUrl())
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        var builder = embed.info(IconRegistry.ICON_BOOK,
                        "📝 Notes — " + target.getUser().getAsTag(),
                        "**" + notes.size() + "** note(s) on record. Use `/deletenote` to remove one by ID.")
                .setThumbnail(target.getEffectiveAvatarUrl());

        // Cap at 10 to stay within Discord's 25-field embed limit
        notes.stream().limit(10).forEach(note ->
                builder.addField(
                        "📝 #" + note.getId() + " — by " + note.getAuthorTag(),
                        note.getContent() + "\n-# <t:" + note.getCreatedAt().getEpochSecond() + ":R>",
                        false
                )
        );

        builder.setTimestamp(Instant.now());
        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }
}