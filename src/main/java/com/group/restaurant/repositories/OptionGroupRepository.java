package com.group.restaurant.repositories;

import com.group.restaurant.models.OptionGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {

    List<OptionGroup> findByRestaurantId(Long restaurantId);

    Page<OptionGroup> findByRestaurantId(Long restaurantId, Pageable pageable);

    Page<OptionGroup> findByRestaurantIdAndNameContainingIgnoreCase(Long restaurantId, String name, Pageable pageable);

    Page<OptionGroup> findByNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
