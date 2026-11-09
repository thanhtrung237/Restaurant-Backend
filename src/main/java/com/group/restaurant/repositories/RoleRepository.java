package com.group.restaurant.repositories;

import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    Optional<Role> findByRestaurantIdAndCode(Long restaurantId, String code);

    List<Role> findByScope(RoleScope scope);

    List<Role> findByRestaurantId(Long restaurantId);

    List<Role> findByRestaurantIdAndScope(Long restaurantId, RoleScope scope);

    boolean existsByCode(String code);

  
}
