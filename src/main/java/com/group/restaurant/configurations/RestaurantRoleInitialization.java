package com.group.restaurant.configurations;

import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.RestaurantDefaultRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestaurantRoleInitialization implements ApplicationRunner {
    private final RestaurantRepository restaurants;
    private final RestaurantDefaultRoles defaultRoles;

    @Override
    public void run(ApplicationArguments args) {
        restaurants.findAllRestaurantIds().forEach(defaultRoles::provision);
    }
}
