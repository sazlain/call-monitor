package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.responses.CallHistoryResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calls/history")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Call History", description = "Historial de llamadas")
public class CallHistoryController {

    private final CallEventJpaRepository           callEventRepo;
    private final CallTypificationJpaRepository    typRepo;
    private final AgentJpaRepository               agentRepo;
    private final UserJpaRepository                userRepo;
    private final LeadJpaRepository                leadRepo;

    public CallHistoryController(CallEventJpaRepository callEventRepo,
                                  CallTypificationJpaRepository typRepo,
                                  AgentJpaRepository agentRepo,
                                  UserJpaRepository userRepo,
                                  LeadJpaRepository leadRepo) {
        this.callEventRepo = callEventRepo;
        this.typRepo       = typRepo;
        this.agentRepo     = agentRepo;
        this.userRepo      = userRepo;
        this.leadRepo      = leadRepo;
    }

    @Operation(summary = "Historial de llamadas paginado")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<Map<String, Object>> history(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String extension,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {

        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<CallEventEntity> pageResult;

        if (isAdmin) {
            pageResult = callEventRepo.findAllHistory(from, to, status, extension, pageable);
        } else {
            // Agente — buscar por email del UserDetails
            String email = userDetails.getUsername();
            AgentEntity agent = userRepo.findByEmail(email)
                    .flatMap(u -> agentRepo.findByUserId(u.getId()))
                    .orElse(null);
            if (agent == null) return ResponseEntity.ok(emptyPage());
            List<String> extensions = List.of(agent.getExtension());
            pageResult = callEventRepo.findHistory(extensions, from, to, status, pageable);
        }

        // Enriquecer con tipificación y lead
        List<String> callIds = pageResult.getContent().stream()
                .map(CallEventEntity::getCallId).toList();

        Map<String, CallTypificationEntity> typMap = typRepo.findByCallIdIn(callIds)
                .stream().collect(Collectors.toMap(
                    CallTypificationEntity::getCallId, t -> t, (a, b) -> a));

        List<CallHistoryResponse> content = pageResult.getContent().stream()
                .map(c -> {
                    CallTypificationEntity typ = typMap.get(c.getCallId());

                    // Datos del agente — normalizar extensión si viene con prefijo
                    String rawExt = c.getCallerExtension();
                    AgentEntity agent = agentRepo.findByExtension(rawExt).orElse(null);
                    if (agent == null && rawExt != null && rawExt.length() > 4) {
                        String suffix = rawExt.substring(rawExt.length() - 4);
                        agent = agentRepo.findByExtension(suffix).orElse(null);
                    }
                    final AgentEntity finalAgent = agent;
                    String agentName = Optional.ofNullable(agent)
                            .flatMap(a -> userRepo.findById(a.getUserId()))
                            .map(u -> u.getName()).orElse(c.getCallerIdName());

                    // Lead
                    LeadEntity lead = typ != null && typ.getLeadId() != null
                            ? leadRepo.findById(typ.getLeadId()).orElse(null) : null;

                    return CallHistoryResponse.builder()
                            .id(c.getId())
                            .callId(c.getCallId())
                            .callerExtension(rawExt)
                            .callerIdNum(c.getCallerIdNum())
                            .callerIdName(c.getCallerIdName())
                            .calledNumber(c.getCalledNumber())
                            .callStatus(c.getCallStatus() != null ? c.getCallStatus().name() : null)
                            .callFlow(c.getCallFlow() != null ? c.getCallFlow().name() : null)
                            .createdAt(c.getCreatedAt())
                            .agentId(finalAgent != null ? finalAgent.getId() : null)
                            .agentName(agentName)
                            .agentExtension(rawExt)
                            .typificationResult(typ != null && typ.getResult() != null ? typ.getResult().name() : null)
                            .typificationNotes(typ != null ? typ.getNotes() : null)
                            .callbackDate(typ != null && typ.getCallbackDate() != null ? typ.getCallbackDate().toString() : null)
                            .leadId(lead != null ? lead.getId() : null)
                            .leadContactName(lead != null ? lead.getContactName() : null)
                            .leadContactPhone(lead != null ? lead.getContactPhone() : null)
                            .build();
                }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content",       content);
        response.put("totalElements", pageResult.getTotalElements());
        response.put("totalPages",    pageResult.getTotalPages());
        response.put("page",          page);
        response.put("size",          size);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> emptyPage() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("content", List.of());
        r.put("totalElements", 0);
        r.put("totalPages", 0);
        r.put("page", 0);
        r.put("size", 0);
        return r;
    }
}
