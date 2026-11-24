package com.group.restaurant.services.guest;

import static org.springframework.http.HttpStatus.*;

import com.group.restaurant.models.Branch;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeofenceService {
    private final Clock clock;

    public GeofenceService() {
        this(Clock.systemUTC());
    }

    GeofenceService(Clock clock) {
        this.clock = clock;
    }

    public static void validateConfiguration(Branch branch) {
        if (branch.isGeofenceEnabled()
                && (!coordinate(branch.getLatitude(), 90)
                        || !coordinate(branch.getLongitude(), 180))) {
            throw new IllegalArgumentException(
                    "Cần cấu hình tọa độ hợp lệ trước khi bật kiểm tra vị trí.");
        }
    }

    public void verify(Branch branch, GuestLocation location, boolean staffCodeVerified) {
        if (!branch.isGeofenceEnabled()) return;
        validateConfiguration(branch);
        if (location == null) {
            if (staffCodeVerified) return;
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Cần chia sẻ vị trí để mở bàn. Nếu không định vị được, vui lòng nhờ nhân viên mở bàn và cung cấp mã tham gia.");
        }
        if (!coordinate(location.latitude(), 90)
                || !coordinate(location.longitude(), 180)
                || location.accuracy() == null
                || !Double.isFinite(location.accuracy())
                || location.accuracy() <= 0
                || location.timestamp() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Dữ liệu vị trí không hợp lệ.");
        }
        long now = clock.millis();
        if (location.timestamp() < now - 120_000 || location.timestamp() > now + 30_000) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Vị trí đã cũ. Vui lòng định vị lại và kiểm tra giờ thiết bị.");
        }
        if (location.accuracy() > branch.getGeofenceMaxAccuracyMeters()) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Vị trí có sai số lớn. Vui lòng thử lại hoặc nhờ nhân viên hỗ trợ mã tham gia.");
        }
        if (distanceMeters(
                        branch.getLatitude(),
                        branch.getLongitude(),
                        location.latitude(),
                        location.longitude())
                > branch.getGeofenceRadiusMeters()) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Bạn đang ngoài phạm vi gọi món của chi nhánh. Vui lòng liên hệ nhân viên nếu đang tại quán.");
        }
    }

    private static boolean coordinate(Double value, int limit) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= limit;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.pow(Math.sin(dLat / 2), 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.pow(Math.sin(dLon / 2), 2);
        a = Math.max(0, Math.min(1, a));
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
