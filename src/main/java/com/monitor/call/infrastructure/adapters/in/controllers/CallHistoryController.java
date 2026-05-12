package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.responses.CallHistoryResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallTypificationJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calls/history")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Call History", description = "Historial de llamadas")
public class CallHistoryController {

    private final CallEventJpaRepository callEventRepo;
    private final CallTypificationJpaRepository typRepo;
    private final AgentJpaRepository agentRepo;
    private final UserJpaRepository userRepo;
    private final LeadJpaRepository leadRepo;
    private final JwtUtil jwtUtil;

    public CallHistoryController(
            CallEventJpaRepository callEventRepo,
            CallTypificationJpaRepository typRepo,
            AgentJpaRepository agentRepo,
            UserJpaRepository userRepo,
            LeadJpaRepository leadRepo,
            JwtUtil jwtUtil
    ) {
        this.callEventRepo = callEventRepo;
        this.typRepo = typRepo;
        this.agentRepo = agentRepo;
        this.userRepo = userRepo;
        this.leadRepo = leadRepo;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "Historial de llamadas paginado")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<Map<String, Object>> history(
            @RequestHeader("Authorization") String auth,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String extension,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "25")
            int size
    ) {

        // =========================
        // VALIDACIONES JWT
        // =========================

        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Token JWT requerido"
            ));
        }

        String token = auth.substring(7);

        Long userId;

        try {
            userId = jwtUtil.extractUserId(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Token inválido"
            ));
        }

        Optional<UserEntity> userOpt = userRepo.findById(userId);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Usuario no encontrado"
            ));
        }

        UserEntity user = userOpt.get();

        boolean isAdmin = user.getRoles() != null &&
                user.getRoles().stream()
                        .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.name()));

        // =========================
        // PAGINACIÓN
        // =========================

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );

        Page<CallEventEntity> pageResult;

        // =========================
        // FILTRO STATUS
        // =========================

        CallStatus callStatus = null;

        if (status != null && !status.isBlank()) {
            try {
                callStatus = CallStatus.valueOf(status.toUpperCase());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Status inválido: " + status
                ));
            }
        }

        // =========================
        // CONSULTA ADMIN
        // =========================

        if (isAdmin) {

            pageResult = callEventRepo.findAllHistory(
                    from,
                    to,
                    callStatus != null ? callStatus.name() : null,
                    extension,
                    pageable
            );

        } else {

            // =========================
            // CONSULTA AGENTE
            // =========================

            AgentEntity agent = agentRepo.findByUserId(userId)
                    .orElse(null);

            if (agent == null || agent.getExtension() == null) {
                return ResponseEntity.ok(emptyPage(page, size));
            }

            List<String> extensions = List.of(agent.getExtension());

            pageResult = callEventRepo.findHistory(
                    extensions,
                    from,
                    to,
                    status,
                    pageable
            );
        }

        // =========================
        // TIPIFICACIONES
        // =========================

        List<String> callIds = pageResult.getContent().stream()
                .map(CallEventEntity::getCallId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, CallTypificationEntity> typMap = typRepo.findByCallIdIn(callIds)
                .stream()
                .collect(Collectors.toMap(
                        CallTypificationEntity::getCallId,
                        t -> t,
                        (a, b) -> a
                ));

        // =========================
        // RESPONSE
        // =========================

        List<CallHistoryResponse> content = pageResult.getContent().stream()
                .map(c -> {

                    CallTypificationEntity typ = typMap.get(c.getCallId());

                    // =========================
                    // AGENTE
                    // =========================

                    String rawExt = c.getCallerExtension();

                    AgentEntity agent = null;

                    if (rawExt != null) {
                        agent = agentRepo.findByExtension(rawExt).orElse(null);

                        if (agent == null && rawExt.length() > 4) {
                            String suffix = rawExt.substring(rawExt.length() - 4);
                            agent = agentRepo.findByExtension(suffix).orElse(null);
                        }
                    }

                    final AgentEntity finalAgent = agent;

                    String agentName = Optional.ofNullable(agent)
                            .flatMap(a -> userRepo.findById(a.getUserId()))
                            .map(UserEntity::getName)
                            .orElse(c.getCallerIdName());

                    // =========================
                    // LEAD
                    // =========================

                    LeadEntity lead = null;

                    if (typ != null && typ.getLeadId() != null) {
                        lead = leadRepo.findById(typ.getLeadId()).orElse(null);
                    }

                    // =========================
                    // DTO
                    // =========================

                    return CallHistoryResponse.builder()
                            .id(c.getId())
                            .callId(c.getCallId())
                            .callerExtension(rawExt)
                            .callerIdNum(c.getCallerIdNum())
                            .callerIdName(c.getCallerIdName())
                            .calledNumber(c.getCalledNumber())
                            .callStatus(
                                    c.getCallStatus() != null
                                            ? c.getCallStatus().name()
                                            : null
                            )
                            .callFlow(
                                    c.getCallFlow() != null
                                            ? c.getCallFlow().name()
                                            : null
                            )
                            .createdAt(c.getCreatedAt())

                            .agentId(
                                    finalAgent != null
                                            ? finalAgent.getId()
                                            : null
                            )

                            .agentName(agentName)
                            .agentExtension(rawExt)

                            .typificationResult(
                                    typ != null && typ.getResult() != null
                                            ? typ.getResult().name()
                                            : null
                            )

                            .typificationNotes(
                                    typ != null
                                            ? typ.getNotes()
                                            : null
                            )

                            .callbackDate(
                                    typ != null && typ.getCallbackDate() != null
                                            ? typ.getCallbackDate().toString()
                                            : null
                            )

                            .leadId(
                                    lead != null
                                            ? lead.getId()
                                            : null
                            )

                            .leadContactName(
                                    lead != null
                                            ? lead.getContactName()
                                            : null
                            )

                            .leadContactPhone(
                                    lead != null
                                            ? lead.getContactPhone()
                                            : null
                            )

                            .build();
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("content", content);
        response.put("totalElements", pageResult.getTotalElements());
        response.put("totalPages", pageResult.getTotalPages());
        response.put("page", page);
        response.put("size", size);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> emptyPage(int page, int size) {

        Map<String, Object> r = new LinkedHashMap<>();

        r.put("content", List.of());
        r.put("totalElements", 0);
        r.put("totalPages", 0);
        r.put("page", page);
        r.put("size", size);

        return r;
    }
}