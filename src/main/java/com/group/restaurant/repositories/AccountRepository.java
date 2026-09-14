package com.group.restaurant.repositories;

import com.group.restaurant.enums.AccountStatus;
import com.group.restaurant.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    Optional<Account> findByRestaurantIdAndEmployeeCode(Long restaurantId, String employeeCode);

    boolean existsByUsername(String username);



    List<Account> findByRestaurantId(Long restaurantId);

    List<Account> findByRestaurantIdAndStatus(Long restaurantId, AccountStatus status);

    long countByRestaurantId(Long restaurantId);

    long countByRestaurantIdAndStatus(Long restaurantId, AccountStatus status);

    //vai tro chi tiet duoc phan cong
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.assignments WHERE a.id = :id")
    Optional<Account> findByIdWithAssignments(@Param("id") Long id);
}
