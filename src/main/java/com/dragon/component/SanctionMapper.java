package com.dragon.component;

import com.dragon.dto.moderation.SanctionResponse;
import com.dragon.entity.SanctionEntity;
import org.springframework.stereotype.Component;

@Component
public class SanctionMapper {

    public SanctionResponse toResponse(SanctionEntity entity) {
        return new SanctionResponse(
                entity.getId(),
                entity.getTargetUserTag(),
                entity.getModeratorUserTag(),
                entity.getType(),
                entity.getStatus(),
                entity.getReason(),
                entity.getDurationSeconds(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.isPermanent()
        );
    }
}
