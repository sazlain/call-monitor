package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.BillingCycle;
import com.monitor.call.domain.enums.LicenseStatus;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
public class UpdateLicenseRequest {
    private String planName;
    private Integer maxAgents;
    private Integer maxCallAgents;
    private Integer maxSalesAgents;
    private LicenseStatus status;
    private BillingCycle billingCycle;
    private BigDecimal priceMonthly;
    private OffsetDateTime expirationDate;
    private String notes;

    /**
     * Acepta tanto fecha completa ISO-8601 ("2026-06-15T00:00:00Z")
     * como solo fecha ("2026-06-15"), convirtiendo al final del día UTC.
     */
    @JsonSetter("expirationDate")
    public void setExpirationDateFromString(String value) {
        if (value == null || value.isBlank()) {
            this.expirationDate = null;
            return;
        }
        if (value.length() == 10) {
            // Solo fecha: "yyyy-MM-dd" → final del día en UTC
            this.expirationDate = LocalDate.parse(value).atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        } else {
            this.expirationDate = OffsetDateTime.parse(value);
        }
    }
}
