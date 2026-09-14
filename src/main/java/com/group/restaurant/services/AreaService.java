package com.group.restaurant.services;

import com.group.restaurant.dtos.request.area.AreaAddRequest;
import com.group.restaurant.dtos.request.area.AreaUpdateRequest;
import com.group.restaurant.dtos.response.area.AreaResponse;
import com.group.restaurant.enums.AreaStatus;

import java.util.List;

public interface AreaService {

    AreaResponse createArea(AreaAddRequest request);

    AreaResponse updateArea(Long id, AreaUpdateRequest request);

    AreaResponse getAreaById(Long id);

    List<AreaResponse> getAreasByBranch(Long branchId, AreaStatus status);

    List<AreaResponse> getAllAreas();

    void changeStatus(Long id, AreaStatus status);

    void deleteArea(Long id);
}
