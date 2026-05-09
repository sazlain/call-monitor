package com.monitor.call.domain.responses;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkLeadResponse {
    private Integer total;
    private Integer created;
    private Integer failed;
    private List<String> errors;
}
