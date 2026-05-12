package com.dragon.repository;


import com.dragon.entity.UserConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsentEntity, Long> {

    Optional<UserConsentEntity> findByMemberId(String memberId);


}
