package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.responses.CallHistoryPage;

import java.time.OffsetDateTime;

public interface CallHistoryRepositoryPort {
    CallHistoryPage findHistory(String extension, String status,
                                OffsetDateTime from, OffsetDateTime to,
                                int page, int size);
}
