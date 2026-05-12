package com.dragon.commands.moderation;

import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.moderation.ModeratorNoteService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandDeleteNote implements SlashCommand {

    private final ModeratorNoteService noteService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "deletenote"; }

    @Override
    public String getDescription() { return "Delete a moderator note by its ID."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.INTEGER, "note_id", "The ID of the note to delete", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.MODERATION, event)) return;

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to delete moderator notes.").build()
            ).setEphemeral(true).queue();
            return;
        }

        long noteId    = Objects.requireNonNull(event.getOption("note_id")).getAsLong();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        boolean deleted = noteService.deleteNote(noteId, guildId);

        if (!deleted) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Note Not Found",
                    "No note with ID **#" + noteId + "** was found in this guild.").build()
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "🗑️ Note Deleted",
                                "Note **#" + noteId + "** has been permanently deleted.")
                        .addField("Deleted by", event.getMember().getAsMention(), true)
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }
}