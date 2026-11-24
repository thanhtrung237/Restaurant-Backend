package com.group.restaurant.services.guest;

import static org.assertj.core.api.Assertions.*;

import com.group.restaurant.models.Branch;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

class GeofenceServiceTest {
    private final long now = 1_800_000_000_000L;
    private final GeofenceService service =
            new GeofenceService(Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC));
    private Branch branch;

    @BeforeEach
    void setup() {
        branch = new Branch();
        branch.setGeofenceEnabled(true);
        branch.setLatitude(10.0);
        branch.setLongitude(106.0);
    }

    @Test
    void nearPositionPasses() {
        assertThatCode(() -> service.verify(branch, location(10.0001, 10, now), false))
                .doesNotThrowAnyException();
    }

    @Test
    void farPositionFailsEvenWithStaffCode() {
        assertThatThrownBy(() -> service.verify(branch, location(11, 10, now), true))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void noPositionRequiresStaffCode() {
        assertThatThrownBy(() -> service.verify(branch, null, false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> service.verify(branch, null, true)).doesNotThrowAnyException();
    }

    @Test
    void poorAccuracyFails() {
        assertThatThrownBy(() -> service.verify(branch, location(10, 300, now), false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void oldAndFuturePositionsFail() {
        assertThatThrownBy(() -> service.verify(branch, location(10, 10, now - 120001), false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.verify(branch, location(10, 10, now + 30001), false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void nonFinitePositionFails() {
        assertThatThrownBy(() -> service.verify(branch, location(Double.NaN, 10, now), false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disabledGeofenceDoesNotRequirePosition() {
        branch.setGeofenceEnabled(false);
        assertThatCode(() -> service.verify(branch, null, false)).doesNotThrowAnyException();
    }

    @Test
    void enabledGeofenceRequiresConfiguredCoordinates() {
        branch.setLatitude(null);
        assertThatThrownBy(() -> GeofenceService.validateConfiguration(branch))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanceHandlesCoincidentAndAntipodalPoints() {
        assertThat(GeofenceService.distanceMeters(10, 106, 10, 106)).isZero();
        assertThat(GeofenceService.distanceMeters(0, 0, 0, 180))
                .isBetween(20_000_000d, 20_100_000d);
        assertThat(GeofenceService.distanceMeters(0, 0, 0.001, 0)).isBetween(111d, 112d);
    }

    private GuestLocation location(double latitude, double accuracy, long timestamp) {
        return new GuestLocation(latitude, 106d, accuracy, timestamp);
    }
}
