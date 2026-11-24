package com.group.restaurant.services;

import com.group.restaurant.dtos.request.role.RoleAddRequest;
import com.group.restaurant.dtos.request.role.RoleUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.role.RoleResponse;
import com.group.restaurant.enums.RoleScope;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface RoleService {

    RoleResponse createRole(RoleAddRequest request);

    RoleResponse updateRole(Long id, RoleUpdateRequest request);

    RoleResponse getRoleById(Long id);

    RoleResponse getRoleByCode(String code);

    PageResponse<RoleResponse> getAllRoles(Long restaurantId, RoleScope scope, Pageable pageable);

    List<RoleResponse> getRolesByRestaurant(Long restaurantId);

    void deleteRole(Long id);
}
