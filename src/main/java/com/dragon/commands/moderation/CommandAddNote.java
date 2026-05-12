package com.dragon.commands.moderation;

import com.dragon.entity.ModeratorNote;
import com.dragon.integration.SlashCommand;
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
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandAddNote implements SlashCommand {

    private final ModeratorNoteService noteService;
    private final Embed embed;

    @Override
    public String getName() { return "addnote"; }

    @Override
    public String getDescription() { return "Add a private staff note to a member's record."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER,   "member", "The member to annotate",             true)
                .addOption(OptionType.STRING, "note",   "Note content (max 1 000 characters)", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to add moderator notes.").build()
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

        String content = Objects.requireNonNull(event.getOption("note")).getAsString();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Member author  = event.getMember();

        try {
            ModeratorNote note = noteService.addNote(
                    target.getId(), target.getUser().getAsTag(),
                    guildId,
                    author.getId(), author.getUser().getAsTag(),
                    content
            );

            event.replyEmbeds(
                    embed.info(IconRegistry.ICON_CHECKS, "📝 Note Added",
                                    "A private note has been added to **" + target.getUser().getAsTag() + "**'s record.")
                            .addField("Note #" + note.getId(), content.length() > 200
                                    ? content.substring(0, 200) + "…" : content, false)
                            .addField("Added by", author.getAsMention(), true)
                            .setThumbnail(target.getEffectiveAvatarUrl())
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();

        } catch (IllegalArgumentException | IllegalStateException e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Cannot Add Note",
                    e.getMessage()).build()
            ).setEphemeral(true).queue();
        }
    }
}
