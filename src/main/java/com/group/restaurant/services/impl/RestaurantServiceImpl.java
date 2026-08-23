package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.restaurant.RestaurantAddRequest;
import com.group.restaurant.dtos.request.restaurant.RestaurantUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.restaurant.RestaurantListResponse;
import com.group.restaurant.dtos.response.restaurant.RestaurantResponse;
import com.group.restaurant.enums.RestaurantStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.RestaurantMapper;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.services.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final AccountRepository accountRepository;
    private final RestaurantMapper restaurantMapper;

    @Override
    public RestaurantResponse createRestaurant(RestaurantAddRequest request) {
        if (restaurantRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Mã nhà hàng '" + request.getCode() + "' đã tồn tại");
        }

        if (restaurantRepository.existsByName(request.getName())) {
            throw new ConflictException("Tên nhà hàng '" + request.getName() + "' đã tồn tại");
        }

        Restaurant restaurant = restaurantMapper.toEntity(request);
        restaurant.setStatus(RestaurantStatus.ACTIVE);

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        return buildRestaurantResponse(savedRestaurant);
    }

    @Override
    public RestaurantResponse updateRestaurant(Long id, RestaurantUpdateRequest request) {
        Restaurant restaurant = findRestaurantById(id);

        if (request.getName() != null && !request.getName().equals(restaurant.getName())) {
            if (restaurantRepository.existsByName(request.getName())) {
                throw new ConflictException("Tên nhà hàng '" + request.getName() + "' đã tồn tại");
            }
        }

        restaurantMapper.updateEntity(restaurant, request);
        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);

        return buildRestaurantResponse(updatedRestaurant);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantById(Long id) {
        Restaurant restaurant = findRestaurantById(id);
        return buildRestaurantResponse(restaurant);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantByCode(String code) {
        Restaurant restaurant = restaurantRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà hàng với mã: " + code));
        return buildRestaurantResponse(restaurant);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RestaurantListResponse> getAllRestaurants(
            String keyword,
            RestaurantStatus status,
            Pageable pageable) {
        Page<Restaurant> restaurantPage = restaurantRepository.findAll(pageable);

        List<RestaurantListResponse> responses = restaurantPage.getContent().stream()
                .map(restaurantMapper::toListResponse)
                .collect(Collectors.toList());

        return PageResponse.<RestaurantListResponse>builder()
                .content(responses)
                .pageNumber(restaurantPage.getNumber())
                .pageSize(restaurantPage.getSize())
                .totalElements(restaurantPage.getTotalElements())
                .totalPages(restaurantPage.getTotalPages())
                .first(restaurantPage.isFirst())
                .last(restaurantPage.isLast())
                .build();
    }

    @Override
    public void changeStatus(Long id, RestaurantStatus status) {
        Restaurant restaurant = findRestaurantById(id);
        restaurant.setStatus(status);
        restaurantRepository.save(restaurant);
    }

    @Override
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = findRestaurantById(id);

        long branchCount = branchRepository.countByRestaurantId(id);
        if (branchCount > 0) {
            throw new ConflictException("Không thể xóa nhà hàng đang có " + branchCount + " chi nhánh");
        }

        long accountCount = accountRepository.countByRestaurantId(id);
        if (accountCount > 0) {
            throw new ConflictException("Không thể xóa nhà hàng đang có " + accountCount + " tài khoản");
        }

        restaurant.softDelete(null);
        restaurantRepository.save(restaurant);
    }

    private Restaurant findRestaurantById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà hàng với ID: " + id));
    }

    private RestaurantResponse buildRestaurantResponse(Restaurant restaurant) {
        RestaurantResponse response = restaurantMapper.toResponse(restaurant);

        Integer branchCount = (int) branchRepository.countByRestaurantId(restaurant.getId());
        Integer accountCount = (int) accountRepository.countByRestaurantId(restaurant.getId());

        response.setBranchCount(branchCount);
        response.setAccountCount(accountCount);

        return response;
    }
}
