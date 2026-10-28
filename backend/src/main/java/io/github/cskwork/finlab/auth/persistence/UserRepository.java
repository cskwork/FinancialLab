package io.github.cskwork.finlab.auth.persistence;

import io.github.cskwork.finlab.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    Optional<User> findByEmailIgnoreCase(String email);
}
