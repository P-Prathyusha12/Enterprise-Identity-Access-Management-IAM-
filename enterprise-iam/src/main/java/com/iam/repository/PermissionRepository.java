package com.iam.repository;

import com.iam.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);

    List<Permission> findByResource(String resource);

    boolean existsByResourceAndAction(String resource, String action);

    boolean existsByName(String name);
}
