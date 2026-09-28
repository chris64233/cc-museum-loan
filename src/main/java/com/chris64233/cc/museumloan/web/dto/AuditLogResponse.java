package com.chris64233.cc.museumloan.web.dto;

import java.time.OffsetDateTime;

public record AuditLogResponse(Long id, String action, String reason, String operator,
                               OffsetDateTime createdAt) {
}
