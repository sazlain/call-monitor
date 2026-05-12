package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.CallHistoryPage;

import java.time.OffsetDateTime;

public interface CallHistoryUseCases {
    CallHistoryPage getCallHistory(String extension, String status,
                                   OffsetDateTime from, OffsetDateTime to,
                                   int page, int size);
}
