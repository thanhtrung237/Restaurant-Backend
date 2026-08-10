package com.group.restaurant.repositories;

import com.group.restaurant.models.DishOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DishOptionGroupRepository extends JpaRepository<DishOptionGroup, Long> {

    List<DishOptionGroup> findByDishId(Long dishId);

    List<DishOptionGroup> findByOptionGroupId(Long optionGroupId);

    Optional<DishOptionGroup> findByDishIdAndOptionGroupId(Long dishId, Long optionGroupId);

    boolean existsByDishIdAndOptionGroupId(Long dishId, Long optionGroupId);

    void deleteByDishId(Long dishId);

    void deleteByOptionGroupId(Long optionGroupId);
}
