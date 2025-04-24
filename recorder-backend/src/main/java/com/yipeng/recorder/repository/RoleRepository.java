package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.Role;
import com.yipeng.recorder.utils.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleType roleType);

    boolean existsByName(RoleType roleType);
}
