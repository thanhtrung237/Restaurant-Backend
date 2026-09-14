package com.group.restaurant.repositories;

import com.group.restaurant.enums.AreaStatus;
import com.group.restaurant.models.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {

    List<Area> findByBranchId(Long branchId);

    List<Area> findByBranchIdAndStatus(Long branchId, AreaStatus status);

    boolean existsByBranchIdAndName(Long branchId, String name);
}
