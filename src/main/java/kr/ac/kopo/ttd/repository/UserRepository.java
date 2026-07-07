package kr.ac.kopo.ttd.repository;

import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByRole(UserRole role);
}
