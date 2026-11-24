package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.role.RoleAddRequest;
import com.group.restaurant.dtos.request.role.RoleUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.role.RoleListResponse;
import com.group.restaurant.dtos.response.role.RoleResponse;
import com.group.restaurant.enums.RoleScope;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.RoleMapper;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.models.Role;
import com.group.restaurant.repositories.AssignmentRepository;
import com.group.restaurant.repositories.RestaurantRepository;
import com.group.restaurant.repositories.RoleRepository;
import com.group.restaurant.services.RoleService;
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
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final AssignmentRepository assignmentRepository;
    private final RoleMapper roleMapper;
    private final TenantGuard tenantGuard;

    @Override
    public RoleResponse createRole(RoleAddRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Mã vai trò '" + request.getCode() + "' đã tồn tại");
        }

        Restaurant restaurant =
                restaurantRepository
                        .findById(request.getRestaurantId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy nhà hàng với ID: "
                                                        + request.getRestaurantId()));

        Role role = roleMapper.toEntity(request);
        role.setRestaurant(restaurant);

        Role savedRole = roleRepository.save(role);
        return buildRoleResponse(savedRole);
    }

    @Override
    public RoleResponse updateRole(Long id, RoleUpdateRequest request) {
        Role role = findRoleById(id);

        roleMapper.updateEntity(role, request);
        Role updatedRole = roleRepository.save(role);

        return buildRoleResponse(updatedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = findRoleById(id);
        return buildRoleResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleByCode(String code) {
        Role role =
                roleRepository
                        .findByCode(code)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy vai trò với mã: " + code));
        return buildRoleResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> getAllRoles(
            Long restaurantId, RoleScope scope, Pageable pageable) {
        tenantGuard.assertRestaurant(restaurantId);
        // Bỏ trống restaurantId thì nhánh cuối gọi findAll() — trả dữ liệu MỌI nhà hàng.
        // Mặc định giới hạn vào nhà hàng của người đăng nhập; chỉ Quản trị nền tảng
        // được xem xuyên nhà hàng.
        if (restaurantId == null && !tenantGuard.isPlatformAdmin()) {
            restaurantId = tenantGuard.currentRestaurantIdOrNull();
        }

        Page<Role> rolePage;

        if (restaurantId != null && scope != null) {
            List<Role> filteredRoles =
                    roleRepository.findByRestaurantIdAndScope(restaurantId, scope);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredRoles.size());
            List<Role> pageContent = filteredRoles.subList(start, end);

            List<RoleResponse> responses =
                    pageContent.stream()
                            .map(this::buildRoleListResponse)
                            .collect(Collectors.toList());

            return PageResponse.<RoleResponse>builder()
                    .content(responses)
                    .pageNumber(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .totalElements((long) filteredRoles.size())
                    .totalPages(
                            (int) Math.ceil((double) filteredRoles.size() / pageable.getPageSize()))
                    .first(pageable.getPageNumber() == 0)
                    .last(end >= filteredRoles.size())
                    .build();
        } else if (restaurantId != null) {
            List<Role> filteredRoles = roleRepository.findByRestaurantId(restaurantId);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredRoles.size());
            List<Role> pageContent = filteredRoles.subList(start, end);

            List<RoleResponse> responses =
                    pageContent.stream()
                            .map(this::buildRoleListResponse)
                            .collect(Collectors.toList());

            return PageResponse.<RoleResponse>builder()
                    .content(responses)
                    .pageNumber(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .totalElements((long) filteredRoles.size())
                    .totalPages(
                            (int) Math.ceil((double) filteredRoles.size() / pageable.getPageSize()))
                    .first(pageable.getPageNumber() == 0)
                    .last(end >= filteredRoles.size())
                    .build();
        } else {
            rolePage = roleRepository.findAll(pageable);

            List<RoleResponse> responses =
                    rolePage.getContent().stream()
                            .map(this::buildRoleListResponse)
                            .collect(Collectors.toList());

            return PageResponse.<RoleResponse>builder()
                    .content(responses)
                    .pageNumber(rolePage.getNumber())
                    .pageSize(rolePage.getSize())
                    .totalElements(rolePage.getTotalElements())
                    .totalPages(rolePage.getTotalPages())
                    .first(rolePage.isFirst())
                    .last(rolePage.isLast())
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getRolesByRestaurant(Long restaurantId) {
        tenantGuard.assertRestaurant(restaurantId);
        List<Role> roles = roleRepository.findByRestaurantId(restaurantId);
        return roles.stream().map(this::buildRoleListResponse).collect(Collectors.toList());
    }

    @Override
    public void deleteRole(Long id) {
        Role role = findRoleById(id);

        long assignmentCount = assignmentRepository.findByRoleId(id).size();
        if (assignmentCount > 0) {
            throw new ConflictException(
                    "Không thể xóa vai trò đang có " + assignmentCount + " phân công");
        }

        role.softDelete(null);
        roleRepository.save(role);
    }

    /** Chốt tenant tại finder: phủ getRoleById / updateRole / deleteRole. */
    private Role findRoleById(Long id) {
        Role role =
                roleRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy vai trò với ID: " + id));
        tenantGuard.assertRestaurant(
                role.getRestaurant() != null ? role.getRestaurant().getId() : null);
        return role;
    }

    private RoleResponse buildRoleResponse(Role role) {
        RoleResponse response = roleMapper.toResponse(role);

        Integer assignmentCount = assignmentRepository.findByRoleId(role.getId()).size();
        response.setAssignmentCount(assignmentCount);

        return response;
    }

    private RoleResponse buildRoleListResponse(Role role) {
        RoleListResponse listResponse = roleMapper.toListResponse(role);

        Integer assignmentCount = assignmentRepository.findByRoleId(role.getId()).size();
        listResponse.setAssignmentCount(assignmentCount);

        // Convert RoleListResponse to RoleResponse for backward compatibility
        return RoleResponse.builder()
                .id(listResponse.getId())
                .name(listResponse.getName())
                .code(listResponse.getCode())
                .scope(listResponse.getScope())
                .assignmentCount(listResponse.getAssignmentCount())
                .build();
    }
}
