package com.group.restaurant.services;

import com.group.restaurant.dtos.request.restaurant.RestaurantAddRequest;
import com.group.restaurant.dtos.request.restaurant.RestaurantUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.restaurant.RestaurantListResponse;
import com.group.restaurant.dtos.response.restaurant.RestaurantResponse;
import com.group.restaurant.enums.RestaurantStatus;
import org.springframework.data.domain.Pageable;

public interface RestaurantService {

    RestaurantResponse createRestaurant(RestaurantAddRequest request);

    RestaurantResponse updateRestaurant(Long id, RestaurantUpdateRequest request);

    RestaurantResponse getRestaurantById(Long id);

    RestaurantResponse getRestaurantByCode(String code);

    PageResponse<RestaurantListResponse> getAllRestaurants(
            String keyword,
            RestaurantStatus status,
            Pageable pageable
    );

    void changeStatus(Long id, RestaurantStatus status);

    void deleteRestaurant(Long id);
}
