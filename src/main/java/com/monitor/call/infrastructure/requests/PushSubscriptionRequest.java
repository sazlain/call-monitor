package com.monitor.call.infrastructure.requests;

import lombok.Data;

@Data
public class PushSubscriptionRequest {
    private String endpoint;
    private String p256dh;
    private String auth;
}
