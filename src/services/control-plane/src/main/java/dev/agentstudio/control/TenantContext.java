package dev.agentstudio.control;

public record TenantContext(String tenantId) {
    public TenantContext { if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("X-Tenant-Id is required"); }
}
