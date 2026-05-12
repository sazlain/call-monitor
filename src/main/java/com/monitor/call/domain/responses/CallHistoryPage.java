package com.monitor.call.domain.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallHistoryPage {
    private List<CallHistoryResponse> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
