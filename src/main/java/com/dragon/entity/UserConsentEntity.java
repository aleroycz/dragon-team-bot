package com.dragon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Entity
@Table(name = "user_consent_v1")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "consent", nullable = false)
    private Boolean consent;

    @Column(name = "consent_requested", nullable = false)
    private Boolean consentRequested;

    // Storing the message consent form id to update the interaction later.
    @Column(name = "discord_message_id")
    private String messageId;

    @CreationTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = true)
    private Timestamp updatedAt;
}
