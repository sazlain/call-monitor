package com.monitor.call.domain.usecases;

import com.monitor.call.domain.ports.in.CallHistoryUseCases;
import com.monitor.call.domain.ports.out.CallHistoryRepositoryPort;
import com.monitor.call.domain.responses.CallHistoryPage;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class CallHistoryImpl implements CallHistoryUseCases {

    private final CallHistoryRepositoryPort historyRepo;

    public CallHistoryImpl(CallHistoryRepositoryPort historyRepo) {
        this.historyRepo = historyRepo;
    }

    @Override
    public CallHistoryPage getCallHistory(String extension, String status,
                                          OffsetDateTime from, OffsetDateTime to,
                                          int page, int size) {
        return historyRepo.findHistory(extension, status, from, to, page, size);
    }
}
