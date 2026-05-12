package com.dragon.service.ticket;

import com.dragon.entity.TicketConfig;
import com.dragon.entity.TicketType;
import com.dragon.repository.TicketConfigRepository;
import com.dragon.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD operations for the ticket system's configuration layer.
 *
 * Separating configuration (this service) from ticket lifecycle operations
 * ({@link TicketService}) keeps each class focused and avoids circular dependencies
 * when {@link TicketService} needs to read config during channel creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketConfigService {

    private static final int MAX_TYPES_PER_GUILD = 5;

    private final TicketConfigRepository configRepository;
    private final TicketTypeRepository typeRepository;

    // ── Config CRUD ───────────────────────────────────────────────────────────

    /**
     * Returns the guild's ticket config, auto-creating a default row if absent.
     * Callers should never receive null — missing config means "not configured yet"
     * rather than "disabled".
     */
    @Transactional
    public TicketConfig getOrCreate(String guildId) {
        return configRepository.findByGuildId(guildId)
                .orElseGet(() -> {
                    TicketConfig fresh = TicketConfig.builder().guildId(guildId).build();
                    TicketConfig saved = configRepository.save(fresh);
                    log.info("[Tickets] Created default config for guild {}", guildId);
                    return saved;
                });
    }

    public Optional<TicketConfig> getConfig(String guildId) {
        return configRepository.findByGuildId(guildId);
    }

    @Transactional
    public TicketConfig setPanelChannel(String guildId, String channelId, String messageId) {
        TicketConfig cfg = getOrCreate(guildId);
        cfg.setPanelChannelId(channelId);
        cfg.setPanelMessageId(messageId);
        return configRepository.save(cfg);
    }

    @Transactional
    public TicketConfig setPanelMessageId(String guildId, String messageId) {
        TicketConfig cfg = getOrCreate(guildId);
        cfg.setPanelMessageId(messageId);
        return configRepository.save(cfg);
    }

    @Transactional
    public TicketConfig setStaffRole(String guildId, String roleId) {
        TicketConfig cfg = getOrCreate(guildId);
        cfg.setStaffRoleId(roleId);
        return configRepository.save(cfg);
    }

    @Transactional
    public TicketConfig setLogChannel(String guildId, String channelId) {
        TicketConfig cfg = getOrCreate(guildId);
        cfg.setLogChannelId(channelId);
        return configRepository.save(cfg);
    }

    @Transactional
    public TicketConfig setCategories(String guildId, String openCatId, String closedCatId) {
        TicketConfig cfg = getOrCreate(guildId);
        cfg.setCategoryOpenId(openCatId);
        cfg.setCategoryClosedId(closedCatId);
        return configRepository.save(cfg);
    }

    @Transactional
    public TicketConfig setEnabled(String guildId, boolean enabled) {
        TicketConfig cfg = getOrCreate(guildId);
        cfg.setEnabled(enabled);
        return configRepository.save(cfg);
    }

    // ── Ticket types ──────────────────────────────────────────────────────────

    /**
     * Adds a new ticket type to the guild's panel.
     *
     * Capped at {@value MAX_TYPES_PER_GUILD} types because the panel uses one
     * button per type and Discord limits action rows per message.
     *
     * @throws IllegalStateException if the guild already has the maximum number of types.
     * @throws IllegalArgumentException if label or emoji is blank.
     */
    @Transactional
    public TicketType addType(String guildId, String label, String emoji, String description) {
        if (label == null || label.isBlank())   throw new IllegalArgumentException("Label cannot be empty.");
        if (emoji == null || emoji.isBlank())   throw new IllegalArgumentException("Emoji cannot be empty.");

        long existing = typeRepository.countByGuildId(guildId);
        if (existing >= MAX_TYPES_PER_GUILD) {
            throw new IllegalStateException(
                    "Maximum of " + MAX_TYPES_PER_GUILD + " ticket types per guild. Remove one first.");
        }

        int nextOrder = (int) existing;
        TicketType type = TicketType.builder()
                .guildId(guildId)
                .label(label.length() > 80 ? label.substring(0, 80) : label)
                .emoji(emoji)
                .description(description != null && !description.isBlank()
                        ? (description.length() > 200 ? description.substring(0, 200) : description)
                        : "Open a " + label + " ticket.")
                .sortOrder(nextOrder)
                .build();

        TicketType saved = typeRepository.save(type);
        log.info("[Tickets] Added type '{}' ({}) to guild {}", label, saved.getId(), guildId);
        return saved;
    }

    /**
     * Removes a ticket type by ID, scoped to the guild.
     *
     * @return true if found and deleted; false if not found.
     */
    @Transactional
    public boolean removeType(Long typeId, String guildId) {
        Optional<TicketType> type = typeRepository.findByIdAndGuildId(typeId, guildId);
        if (type.isEmpty()) return false;
        typeRepository.delete(type.get());
        log.info("[Tickets] Removed type #{} from guild {}", typeId, guildId);
        return true;
    }

    /** Returns only enabled types for the panel, sorted by sortOrder. */
    public List<TicketType> getEnabledTypes(String guildId) {
        return typeRepository.findByGuildIdAndEnabledTrueOrderBySortOrderAsc(guildId);
    }

    /** Returns all types (including disabled) for the management command. */
    public List<TicketType> getAllTypes(String guildId) {
        return typeRepository.findByGuildIdOrderBySortOrderAsc(guildId);
    }
}
