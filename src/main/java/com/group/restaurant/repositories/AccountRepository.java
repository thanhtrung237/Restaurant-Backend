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

    // Load account kèm assignments để dùng trong UserDetailsService
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.assignments ass LEFT JOIN FETCH ass.role WHERE a.username = :username")
    Optional<Account> findByUsernameWithAssignments(@Param("username") String username);

    // Tìm theo restaurantCode + username (FR-ADM-04)
    @Query("SELECT DISTINCT a FROM Account a JOIN FETCH a.restaurant r LEFT JOIN FETCH a.assignments ass LEFT JOIN FETCH ass.role LEFT JOIN FETCH ass.branch WHERE r.code = :restaurantCode AND a.username = :username")
    Optional<Account> findByRestaurantCodeAndUsername(
            @Param("restaurantCode") String restaurantCode,
            @Param("username") String username);

    boolean existsByUsername(String username);



    List<Account> findByRestaurantId(Long restaurantId);

    List<Account> findByRestaurantIdAndStatus(Long restaurantId, AccountStatus status);

    long countByRestaurantId(Long restaurantId);

    long countByRestaurantIdAndStatus(Long restaurantId, AccountStatus status);

    //vai tro chi tiet duoc phan cong
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.assignments WHERE a.id = :id")
    Optional<Account> findByIdWithAssignments(@Param("id") Long id);
}
