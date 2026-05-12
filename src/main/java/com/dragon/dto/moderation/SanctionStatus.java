// ─────────────────────────────────────────────────────────────────────────────
// SanctionStatus.java
// ─────────────────────────────────────────────────────────────────────────────
package com.dragon.dto.moderation;

public enum SanctionStatus {
    /** Sanction is currently active */
    ACTIVE,
    /** Sanction has expired naturally */
    EXPIRED,
    /** Sanction was manually revoked by staff */
    REVOKED
}