package com.monitor.call.infrastructure.services;

import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of SystemConfigUseCases for this worktree.
 * Returns null/defaults for all config lookups; the schedule logic
 * treats null as FREE (no restriction).
 */
@Service
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
}
