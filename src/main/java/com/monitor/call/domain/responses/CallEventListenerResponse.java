package com.monitor.call.domain.responses;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallEventListenerResponse {
    private String callId;
    private String status;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
