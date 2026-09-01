package com.group.restaurant.services;

import com.group.restaurant.dtos.request.tableSession.TableSessionOpenRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionUpdateRequest;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;

import java.util.List;

public interface TableSessionService {

    TableSessionResponse openSession(TableSessionOpenRequest request);

    TableSessionResponse updateSession(Long id, TableSessionUpdateRequest request);

    TableSessionResponse getSessionById(Long id);

    TableSessionResponse getOpenSessionByTable(Long tableId);

    List<TableSessionResponse> getOpenSessionsByBranch(Long branchId);

    List<TableSessionResponse> getSessionsByTable(Long tableId);

    TableSessionResponse closeSession(Long id);

    TableSessionResponse cancelSession(Long id);

    void deleteSession(Long id);
}
