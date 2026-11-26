package com.group.restaurant.services.impl;

import com.group.restaurant.services.OrderNotificationService;
import com.group.restaurant.dtos.request.area.AreaAddRequest;
import com.group.restaurant.dtos.request.area.AreaUpdateRequest;
import com.group.restaurant.dtos.response.area.AreaResponse;
import com.group.restaurant.enums.AreaStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.AreaMapper;
import com.group.restaurant.models.Area;
import com.group.restaurant.models.Branch;
import com.group.restaurant.repositories.AreaRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.services.AreaService;
import com.group.restaurant.utils.TenantGuard;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AreaServiceImpl implements AreaService {

    private final AreaRepository areaRepository;
    private final OrderNotificationService orderNotificationService;
    private final BranchRepository branchRepository;
    private final DiningTableRepository diningTableRepository;
    private final AreaMapper areaMapper;
    private final TenantGuard tenantGuard;

    @Override
    public AreaResponse createArea(AreaAddRequest request) {
        tenantGuard.assertBranchAssigned(request.getBranchId(), TenantGuard.ROLE_BRANCH_MANAGER);
        Branch branch =
                branchRepository
                        .findById(request.getBranchId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy chi nhánh với ID: "
                                                        + request.getBranchId()));

        String name = request.getName().strip();
        if (areaRepository.existsByBranchIdAndNameIgnoreCase(request.getBranchId(), name)) {
            throw new ConflictException("Khu vực '" + name + "' đã tồn tại trong chi nhánh này");
        }
        var deletedId = areaRepository.findDeletedId(branch.getId(), name);
        if (deletedId.isPresent()) {
            Long id = deletedId.get();
            if (areaRepository.restoreDeleted(id, AreaStatus.ACTIVE.getValue()) == 0) {
                throw new ConflictException(
                        "Khu vực '" + name + "' đã tồn tại trong chi nhánh này");
            }
            return buildAreaResponse(findAreaById(id));
        }

        Area area = areaMapper.toEntity(request);
        area.setName(name);
        area.setBranch(branch);
        area.setStatus(AreaStatus.ACTIVE);

        Area savedArea = areaRepository.save(area);
        orderNotificationService.notifyManagementUpdated(area.getBranch().getRestaurant().getId());
        return buildAreaResponse(savedArea);
    }

    @Override
    public AreaResponse updateArea(Long id, AreaUpdateRequest request) {
        Area area = findAreaForWrite(id);

        if (!area.getName().equalsIgnoreCase(request.getName())
                && areaRepository.existsByBranchIdAndNameIgnoreCase(
                        area.getBranch().getId(), request.getName())) {
            throw new ConflictException(
                    "Khu vực '" + request.getName() + "' đã tồn tại trong chi nhánh này");
        }

        areaMapper.updateEntity(area, request);
        Area updatedArea = areaRepository.save(area);
        orderNotificationService.notifyManagementUpdated(area.getBranch().getRestaurant().getId());
        return buildAreaResponse(updatedArea);
    }

    @Override
    @Transactional(readOnly = true)
    public AreaResponse getAreaById(Long id) {
        Area area = findAreaById(id);
        return buildAreaResponse(area);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AreaResponse> getAreasByBranch(Long branchId, AreaStatus status) {
        tenantGuard.assertBranch(branchId);
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId);
        }

        List<Area> areas =
                (status != null)
                        ? areaRepository.findByBranchIdAndStatus(branchId, status)
                        : areaRepository.findByBranchId(branchId);

        return areas.stream().map(this::buildAreaResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AreaResponse> getAllAreas() {
        // Hàm này không có tham số lọc nào — trả TẤT CẢ khu vực của mọi nhà hàng.
        // Giới hạn theo nhà hàng của người đăng nhập (Quản trị nền tảng vẫn xem được hết).
        Long currentRestaurantId =
                tenantGuard.isPlatformAdmin() ? null : tenantGuard.currentRestaurantIdOrNull();
        if (currentRestaurantId != null) {
            return areaRepository.findAll().stream()
                    .filter(
                            a ->
                                    a.getBranch() != null
                                            && a.getBranch().getRestaurant() != null
                                            && currentRestaurantId.equals(
                                                    a.getBranch().getRestaurant().getId()))
                    .map(this::buildAreaResponse)
                    .collect(Collectors.toList());
        }
        return areaRepository.findAll().stream()
                .map(this::buildAreaResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void changeStatus(Long id, AreaStatus status) {
        Area area = findAreaForWrite(id);
        area.setStatus(status);
        areaRepository.save(area);
        orderNotificationService.notifyManagementUpdated(area.getBranch().getRestaurant().getId());
    }

    @Override
    public void deleteArea(Long id) {
        Area area = findAreaForWrite(id);
        if (diningTableRepository.existsByAreaId(id)) {
            throw new ConflictException(
                    "Khu vực còn bàn. Hãy chuyển các bàn sang khu vực khác trước khi xóa.");
        }
        area.softDelete(null);
        areaRepository.save(area);
        orderNotificationService.notifyManagementUpdated(area.getBranch().getRestaurant().getId());
    }

    private Area findAreaForWrite(Long id) {
        Area area =
                areaRepository
                        .findForTableAssignment(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy khu vực với ID: " + id));
        tenantGuard.assertBranchAssigned(area.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        return area;
    }

    private Area findAreaById(Long id) {
        Area area =
                areaRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy khu vực với ID: " + id));
        tenantGuard.assertBranch(area.getBranch());
        return area;
    }

    private AreaResponse buildAreaResponse(Area area) {
        AreaResponse response = areaMapper.toResponse(area);
        int tableCount = diningTableRepository.findByAreaId(area.getId()).size();
        response.setTableCount(tableCount);
        return response;
    }
}
