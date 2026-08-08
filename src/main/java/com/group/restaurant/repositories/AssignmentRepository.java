package com.group.restaurant.repositories;

import com.group.restaurant.models.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByAccountId(Long accountId);

    List<Assignment> findByRoleId(Long roleId);

    List<Assignment> findByBranchId(Long branchId);

    void deleteByAccountId(Long accountId);

    void deleteByRoleId(Long roleId);
}
