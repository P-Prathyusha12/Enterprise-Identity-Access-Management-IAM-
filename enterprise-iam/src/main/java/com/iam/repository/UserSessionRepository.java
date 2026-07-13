package com.iam.repository;

import com.iam.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionId(String sessionId);

    List<UserSession> findByUserIdAndActiveTrue(Long userId);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false WHERE us.sessionId = :sessionId")
    void deactivateBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("UPDATE UserSession us SET us.active = false WHERE us.user.id = :userId")
    void deactivateAllByUserId(@Param("userId") Long userId);
}
