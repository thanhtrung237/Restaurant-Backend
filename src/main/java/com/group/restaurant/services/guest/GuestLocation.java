package com.group.restaurant.services.guest;

import jakarta.validation.constraints.*;

/** Untrusted device report. Never a proof of physical presence. */
public record GuestLocation(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @DecimalMin("0.01") @DecimalMax("100000") Double accuracy,
        @NotNull Long timestamp) {
}
