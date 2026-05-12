package com.dragon.commands;

import com.dragon.component.IconRenderer;
import com.dragon.component.VoiceScoreChart;
import com.dragon.entity.VoiceScore;
import com.dragon.integration.SlashCommand;
import com.dragon.repository.VoiceScoreRepository;
import com.dragon.service.UserConsentService;
import com.dragon.utils.chart.dataset.TemporalBasis;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CommandCommStatistics extends ListenerAdapter implements SlashCommand {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final UserConsentService userConsentService;
    private final VoiceScoreRepository voiceScoreRepository;
    private final VoiceScoreChart voiceScoreChart;
    private final IconRenderer iconRenderer;

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public String getDescription() {
        return "View voice communication statistics and score history.";
    }

    @Override
    public void configure(SlashCommandData data) {
        data.addOption(
                OptionType.USER,
                "user",
                "User to inspect (defaults to yourself)",
                false
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        var target = event.getOption("user") != null
                ? event.getOption("user").getAsUser()
                : event.getUser();

        String userId = target.getId();
        String username = target.getEffectiveName();
        String avatar = target.getEffectiveAvatarUrl() + "?size=256";

        long total = voiceScoreRepository.countByUserId(userId);

        event.deferReply().queue(hook -> {

            try {

                var last5 = voiceScoreRepository.findLast5ByUserId(userId);
                var allData = voiceScoreRepository.findByUserIdOrderByRecordedAtDesc(userId);

                double avg = voiceScoreRepository
                        .findAverageScoreByUserId(userId)
                        .orElse(0.0);

                int best = voiceScoreRepository
                        .findBestScoreByUserId(userId)
                        .orElse(0);

                int worst = voiceScoreRepository
                        .findWorstScoreByUserId(userId)
                        .orElse(0);

                var bestEntry = voiceScoreRepository.findBestEntryByUserId(userId);

                List<FileUpload> uploads = new ArrayList<>();

                FileUpload chartUpload = getChartUpload(username, allData);
                uploads.add(chartUpload);

                uploads.addAll(buildMetricUploads(total, avg, best, worst));

                for (int i = 0; i < last5.size(); i++) {
                    uploads.add(buildBandUpload(last5.get(i).getScore(), i));
                }

                uploads.add(buildActionUpload(
                        IconRenderer.Action.REFRESH,
                        "action_refresh.png"
                ));

                uploads.add(buildActionUpload(
                        IconRenderer.Action.FULL_HISTORY,
                        "action_history.png"
                ));

                Container container = buildContainer(
                        userId,
                        username,
                        avatar,
                        total,
                        avg,
                        best,
                        worst,
                        last5,
                        bestEntry
                );

                hook.sendMessageComponents(container)
                        .useComponentsV2()
                        .addFiles(uploads)
                        .queue();

            } catch (Exception ex) {

                hook.sendMessage(
                                "Failed to render statistics: " + ex.getMessage()
                        )
                        .setEphemeral(true)
                        .queue();
            }
        });
    }

    private Container buildContainer(
            String userId,
            String username,
            String avatar,
            long total,
            double avg,
            int best,
            int worst,
            List<VoiceScore> last5,
            Optional<VoiceScore> bestEntry
    ) {

        boolean isActive = this.userConsentService.hasConsent(UserSnowflake.fromId(userId));

        List<ContainerChildComponent> parts = new ArrayList<>();

        parts.add(
                Section.of(
                        Thumbnail.fromUrl(avatar),
                        TextDisplay.of("# Voice Statistics"),
                        TextDisplay.of(username)
                )
        );

        parts.add(
                Separator.createDivider(
                        Separator.Spacing.SMALL
                )
        );

        parts.add(TextDisplay.of("## Performance Metrics"));

        parts.add(
                MediaGallery.of(
                        MediaGalleryItem.fromUrl("attachment://metric_sessions.png"),
                        MediaGalleryItem.fromUrl("attachment://metric_average.png"),
                        MediaGalleryItem.fromUrl("attachment://metric_best.png"),
                        MediaGalleryItem.fromUrl("attachment://metric_worst.png")
                )
        );

        parts.add(
                Separator.createDivider(
                        Separator.Spacing.SMALL
                )
        );

        parts.add(TextDisplay.of("## Score Trend"));

        parts.add(
                MediaGallery.of(
                        MediaGalleryItem.fromUrl("attachment://chart.png")
                )
        );

        parts.add(
                Separator.createDivider(
                        Separator.Spacing.SMALL
                )
        );

        parts.add(
                TextDisplay.of(
                        "## Recent Feedback — Last " + last5.size() + " Sessions"
                )
        );

        List<VoiceScore> ordered = new ArrayList<>(last5);
        Collections.reverse(ordered);

        for (int i = 0; i < ordered.size(); i++) {

            VoiceScore vs = ordered.get(i);

            String date = vs.getRecordedAt()
                    .format(DATE_FMT);

            String fb = (
                    vs.getFeedback() != null &&
                            !vs.getFeedback().isBlank()
            )
                    ? vs.getFeedback()
                    : "No feedback recorded.";

            parts.add(
                    Section.of(
                            Thumbnail.fromUrl(
                                    "attachment://band_" + i + ".png"
                            ),
                            TextDisplay.of(
                                    "**" + date + "** · `" +
                                            vs.getScore() + "/10`"
                            ),
                            TextDisplay.of("-# " + fb)
                    )
            );
        }

        bestEntry.ifPresent(be -> {

            parts.add(
                    Separator.createDivider(
                            Separator.Spacing.SMALL
                    )
            );

            String date = be.getRecordedAt()
                    .format(DATE_FMT);

            String fb = (
                    be.getFeedback() != null &&
                            !be.getFeedback().isBlank()
            )
                    ? be.getFeedback()
                    : "No feedback recorded.";

            parts.add(
                    TextDisplay.of(
                            "## All-time Best Session\n" +
                                    "`" + be.getScore() +
                                    "/10` · " + date +
                                    "\n-# " + fb
                    )
            );
        });

        parts.add(Separator.create(false, Separator.Spacing.SMALL));
        parts.add(TextDisplay.of("- These statistics are only for improvements purposes and do not count as performance metric requirements."));

        if (!isActive) {
            // Notice of AI-Assisted evaluation being turned off.
            parts.add(Separator.create(false, Separator.Spacing.LARGE));
            parts.add(Section.of(
                    Thumbnail.fromUrl("https://cdn.discordapp.com/attachments/1501768878051692624/1501777489695871086/1778122061238.png?ex=6a014347&is=69fff1c7&hm=7b2e18e8ad313d4c5ecd328274f2815567d81c83d581173cad7fdc5a4745ba47&"),
                    TextDisplay.of("**Notice:**"),
                    TextDisplay.of("-# AI-Assisted evaluation is currently turned off for your profile. If you wish to use this feature please use /user-consent."),
                    TextDisplay.of("-# This features requires your consent due to the usage of Voice transcription.")
            ));
        }

        return Container.of(parts);
    }

    private List<FileUpload> buildMetricUploads(
            long total,
            double avg,
            int best,
            int worst
    ) throws Exception {

        List<FileUpload> uploads = new ArrayList<>();

        uploads.add(
                FileUpload.fromData(
                        iconRenderer.statMetricIcon(
                                IconRenderer.Metric.SESSIONS,
                                String.valueOf(total),
                                160,
                                64
                        ),
                        "metric_sessions.png"
                )
        );

        uploads.add(
                FileUpload.fromData(
                        iconRenderer.statMetricIcon(
                                IconRenderer.Metric.AVERAGE,
                                String.format("%.2f", avg),
                                160,
                                64
                        ),
                        "metric_average.png"
                )
        );

        uploads.add(
                FileUpload.fromData(
                        iconRenderer.statMetricIcon(
                                IconRenderer.Metric.BEST,
                                best + "/10",
                                160,
                                64
                        ),
                        "metric_best.png"
                )
        );

        uploads.add(
                FileUpload.fromData(
                        iconRenderer.statMetricIcon(
                                IconRenderer.Metric.WORST,
                                worst + "/10",
                                160,
                                64
                        ),
                        "metric_worst.png"
                )
        );

        return uploads;
    }

    private FileUpload buildBandUpload(int score, int index) throws Exception {

        return FileUpload.fromData(
                iconRenderer.scoreBandIcon(
                        mapBand(score),
                        160,
                        40
                ),
                "band_" + index + ".png"
        );
    }

    private FileUpload buildActionUpload(
            IconRenderer.Action action,
            String fileName
    ) throws Exception {

        return FileUpload.fromData(
                iconRenderer.actionButtonIcon(
                        action,
                        160,
                        40
                ),
                fileName
        );
    }

    private IconRenderer.Band mapBand(int score) {

        if (score >= 9) {
            return IconRenderer.Band.ACTIONABLE;
        }

        if (score >= 7) {
            return IconRenderer.Band.CLEAR;
        }

        if (score >= 5) {
            return IconRenderer.Band.VAGUE;
        }

        if (score >= 3) {
            return IconRenderer.Band.CONFUSING;
        }

        if (score >= 1) {
            return IconRenderer.Band.INCOHERENT;
        }

        return IconRenderer.Band.NO_CONTENT;
    }

    private FileUpload getChartUpload(
            String username,
            List<VoiceScore> allData
    ) {

        Pair<Boolean, Pair<String, FileUpload>> result =
                voiceScoreChart.getScoreTrendChart(
                        username,
                        allData,
                        TemporalBasis.DAILY
                );

        if (
                result == null ||
                        !result.getLeft() ||
                        result.getRight() == null
        ) {

            throw new IllegalStateException(
                    "Chart generation failed for user: " + username
            );
        }

        return result.getRight().getRight();
    }
}
