package com.group.restaurant.repositories;

import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.models.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    Optional<Role> findByRestaurantIdAndCode(Long restaurantId, String code);

    List<Role> findByScope(RoleScope scope);

    List<Role> findByRestaurantId(Long restaurantId);

    @Query(value = "select code from role where restaurant_id = :restaurantId", nativeQuery = true)
    List<String> findCodesIncludingDeleted(Long restaurantId);

    List<Role> findByRestaurantIdAndScope(Long restaurantId, RoleScope scope);

    boolean existsByCode(String code);
}
