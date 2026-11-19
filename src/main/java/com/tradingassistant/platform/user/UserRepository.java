package com.tradingassistant.platform.user;

import com.tradingassistant.platform.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for accounts.
 */
public interface UserRepository extends MongoRepository<UserDocument, String> {

    Optional<UserDocument> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<UserDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<UserDocument> findByStatus(UserStatus status);

    long countByRolesContaining(Role role);

    long countByRolesContainingAndStatus(Role role, UserStatus status);
}
