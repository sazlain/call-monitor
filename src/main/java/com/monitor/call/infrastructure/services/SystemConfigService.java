package com.monitor.call.infrastructure.services;

import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.responses.SystemConfigResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stub implementation of SystemConfigUseCases for this worktree.
 * Returns null/defaults for all config lookups; the schedule logic
 * treats null as FREE (no restriction).
 */
public class SystemConfigService implements SystemConfigUseCases {

    @Override
    public String getValue(Long adminId, String key) {
        return null;
    }

    @Override
    public boolean getBooleanValue(Long adminId, String key) {
        return false;
    }

    @Override
    public int getIntValue(Long adminId, String key) {
        return 0;
    }

    @Override
    public List<SystemConfigResponse> listByAdmin(Long adminId) {
        return List.of();
    }

    @Override
    public SystemConfigResponse getByKey(Long adminId, String key) {
        return null;
    }

    @Override
    public SystemConfigResponse upsert(Long adminId, String key, String value) {
        return null;
    }

    @Override
    public void seedDefaults(Long adminId) {
        // stub
    }
}
