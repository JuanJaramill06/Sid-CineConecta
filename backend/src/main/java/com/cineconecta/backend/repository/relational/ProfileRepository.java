package com.cineconecta.backend.repository.relational;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cineconecta.backend.model.relational.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Integer> {

    List<Profile> findByAccount_AccountIdOrderByProfileIdAsc(Integer accountId);

    // Ownership check (RNF2): never trust a profileId sent by the client.
    Optional<Profile> findByProfileIdAndAccount_AccountId(Integer profileId, Integer accountId);

    // Pass Boolean.TRUE: the parameter goes through the 'Y'/'N' converter.
    long countByAccount_AccountIdAndActive(Integer accountId, Boolean active);
}
