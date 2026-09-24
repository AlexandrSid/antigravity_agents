package com.userservice.repository;

import com.userservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndIsDeletedFalse(Long id);

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByPhoneNumberAndIsDeletedFalse(String phoneNumber);

    boolean existsByEmailAndIsDeletedFalseAndIdNot(String email, Long id);

    boolean existsByPhoneNumberAndIsDeletedFalseAndIdNot(String phoneNumber, Long id);

    @Query("""
            SELECT u FROM User u
            WHERE (u.father.id = :parentId OR u.mother.id = :parentId)
              AND u.isDeleted = false
            """)
    List<User> findActiveChildren(@Param("parentId") Long parentId);
}
