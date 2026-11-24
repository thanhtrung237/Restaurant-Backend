package com.group.restaurant.services;

import com.group.restaurant.dtos.request.assignment.AssignmentAddRequest;
import com.group.restaurant.dtos.response.assignment.AssignmentResponse;
import java.util.List;

public interface AssignmentService {

    AssignmentResponse createAssignment(AssignmentAddRequest request);

    AssignmentResponse getAssignmentById(Long id);

    List<AssignmentResponse> getAssignmentsByAccount(Long accountId);

    List<AssignmentResponse> getAssignmentsByRole(Long roleId);

    List<AssignmentResponse> getAssignmentsByBranch(Long branchId);

    void deleteAssignment(Long id);
}
