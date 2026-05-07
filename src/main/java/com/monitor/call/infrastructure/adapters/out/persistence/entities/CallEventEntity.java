package com.monitor.call.infrastructure.adapters.out.persistence.entities;


import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "call_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallEventEntity {

    @Id
    @Column(name = "call_id", nullable = false, unique = true)
    private String callId;

    @Column(name = "caller_id_num")
    private String callerIdNum;

    @Column(name = "caller_id_name")
    private String callerIdName;

    @Column(name = "called_did")
    private String calledDID;

    @Column(name = "called_extension")
    private String calledExtension;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_status")
    private CallStatus callStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_flow")
    private CallFlow callFlow;

    @Column(name = "caller_extension")
    private String callerExtension;

    @Column(name = "called_number")
    private String calledNumber;

    @Column(name = "call_api_id")
    private String callAPIID;
}
