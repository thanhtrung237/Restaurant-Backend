package com.group.restaurant.services.impl;

import com.group.restaurant.services.OrderNotificationService;
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
import com.group.restaurant.services.AuditLogService;
import com.group.restaurant.services.RestaurantService;
import com.group.restaurant.services.audit.AuditAction;
import com.group.restaurant.services.audit.AuditEvent;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final OrderNotificationService orderNotificationService;
    private final BranchRepository branchRepository;
    private final AccountRepository accountRepository;
    private final RestaurantMapper restaurantMapper;
    private final TenantGuard tenantGuard;
    private final AuditLogService auditLogService;

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
        orderNotificationService.notifyStructureUpdated(restaurant.getId());
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

        // Chụp ảnh giá trị TRƯỚC khi mapper ghi đè, để nhật ký có đủ trước/sau (BR-12).
        String configBefore = configSnapshot(restaurant);

        restaurantMapper.updateEntity(restaurant, request);

        /*
         * Chuẩn hoá chuỗi rỗng thành null — cùng cách đã làm cho cấu hình chi nhánh (be-issues mục 7).
         *
         * Mapper dùng `nullValuePropertyMappingStrategy = IGNORE`, nên "không gửi field" nghĩa là
         * GIỮ NGUYÊN, còn gửi chuỗi rỗng là cách duy nhất để XOÁ TRẮNG. Không chuẩn hoá thì cùng
         * một ô trống lúc lưu ra "" lúc ra null, và so sánh trước/sau trong nhật ký cũng lệch.
         */
        restaurant.setTaxCode(blankToNull(restaurant.getTaxCode()));
        restaurant.setAddress(blankToNull(restaurant.getAddress()));
        restaurant.setPhone(blankToNull(restaurant.getPhone()));
        restaurant.setLogoUrl(blankToNull(restaurant.getLogoUrl()));

        Restaurant updatedRestaurant = restaurantRepository.save(restaurant);
        orderNotificationService.notifyStructureUpdated(restaurant.getId());

        // BR-12: "thay đổi cấu hình chi nhánh/NHÀ HÀNG phải sinh bản ghi nhật ký".
        // Mã số thuế, tên và địa chỉ ở đây được IN LÊN HOÁ ĐƠN (FR-ADM-03) nên đổi phải có vết.
        auditLogService.record(
                AuditEvent.builder()
                        .entityType("Restaurant")
                        .entityId(updatedRestaurant.getId())
                        .actionType(AuditAction.CONFIG_CHANGE)
                        .restaurantId(updatedRestaurant.getId())
                        .valueBefore(configBefore)
                        .valueAfter(configSnapshot(updatedRestaurant))
                        .reason("Quản trị NH đổi cấu hình nhà hàng")
                        .build());

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
        Restaurant restaurant =
                restaurantRepository
                        .findByCode(code)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhà hàng với mã: " + code));
        tenantGuard.assertRestaurant(restaurant.getId());
        return buildRestaurantResponse(restaurant);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RestaurantListResponse> getAllRestaurants(
            String keyword, RestaurantStatus status, Pageable pageable) {
        Page<Restaurant> restaurantPage = restaurantRepository.findAll(pageable);

        // Danh sách nhà hàng là dữ liệu cấp nền tảng. Quản trị nhà hàng chỉ được thấy
        // nhà hàng của chính mình, không phải toàn bộ khách hàng khác của hệ thống.
        Long currentRestaurantId =
                tenantGuard.isPlatformAdmin() ? null : tenantGuard.currentRestaurantIdOrNull();

        List<RestaurantListResponse> responses =
                restaurantPage.getContent().stream()
                        .filter(
                                r ->
                                        currentRestaurantId == null
                                                || currentRestaurantId.equals(r.getId()))
                        .map(
                                r -> {
                                    RestaurantListResponse resp =
                                            restaurantMapper.toListResponse(r);
                                    resp.setBranchCount(
                                            (int) branchRepository.countByRestaurantId(r.getId()));
                                    return resp;
                                })
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
        orderNotificationService.notifyStructureUpdated(restaurant.getId());
    }

    @Override
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = findRestaurantById(id);

        long branchCount = branchRepository.countByRestaurantId(id);
        if (branchCount > 0) {
            throw new ConflictException(
                    "Không thể xóa nhà hàng đang có " + branchCount + " chi nhánh");
        }

        long accountCount = accountRepository.countByRestaurantId(id);
        if (accountCount > 0) {
            throw new ConflictException(
                    "Không thể xóa nhà hàng đang có " + accountCount + " tài khoản");
        }

        restaurant.softDelete(null);
        restaurantRepository.save(restaurant);
        orderNotificationService.notifyStructureUpdated(restaurant.getId());
    }

    /**
     * Chốt tenant tại finder: phủ getRestaurantById / updateRestaurant / changeStatus /
     * deleteRestaurant. Quản trị nhà hàng chỉ thao tác được trên nhà hàng của mình;
     * Quản trị nền tảng đi xuyên (xem TenantGuard).
     */
    /** Chuỗi rỗng / chỉ khoảng trắng ⇒ null, để cột trống luôn nhất quán một giá trị. */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Ảnh cấu hình nhà hàng, để so sánh trước/sau trong nhật ký (FR-ADM-03). */
    private String configSnapshot(Restaurant r) {
        return "name="
                + r.getName()
                + "; taxCode="
                + r.getTaxCode()
                + "; address="
                + r.getAddress()
                + "; phone="
                + r.getPhone()
                + "; logoUrl="
                + r.getLogoUrl();
    }

    private Restaurant findRestaurantById(Long id) {
        Restaurant restaurant =
                restaurantRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhà hàng với ID: " + id));
        tenantGuard.assertRestaurant(restaurant.getId());
        return restaurant;
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
