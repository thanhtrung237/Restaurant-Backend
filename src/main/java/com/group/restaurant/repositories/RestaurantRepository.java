package com.group.restaurant.repositories;

import com.group.restaurant.models.Restaurant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Restaurant r where r.id = :id")
    Optional<Restaurant> findByIdForUpdate(Long id);

    @Query("select r.id from Restaurant r order by r.id")
    List<Long> findAllRestaurantIds();

    Optional<Restaurant> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
