package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.domain.responses.BulkLeadResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.CreateLeadRequest;
import com.monitor.call.infrastructure.mappers.LeadMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class LeadImpl implements LeadUseCases {

    private static final Logger logger = LoggerFactory.getLogger(LeadImpl.class);

    private final LeadRepositoryPort leadRepo;
    private final UserJpaRepository userRepo;
    private final AgentJpaRepository agentRepo;
    private final AgentRepositoryPort agentRepositoryPort;
    private final SystemConfigUseCases configUseCases;

    /** Round-robin index por admin (en memoria; se reinicia al reiniciar el servicio) */
    private final Map<Long, AtomicInteger> roundRobinIndex = new ConcurrentHashMap<>();

    public LeadImpl(LeadRepositoryPort leadRepo,
                    UserJpaRepository userRepo,
                    AgentJpaRepository agentRepo,
                    AgentRepositoryPort agentRepositoryPort,
                    SystemConfigUseCases configUseCases) {
        this.leadRepo = leadRepo;
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.agentRepositoryPort = agentRepositoryPort;
        this.configUseCases = configUseCases;
    }

    @Override
    @Transactional
    public LeadResponse createLead(CreateLeadRequest request, Long ownerId) {
        Long assignedAgentId = request.getAssignedAgentId();
        if (assignedAgentId == null) {
            assignedAgentId = resolveRoundRobinAgent(ownerId);
        }
        LeadStatus status = request.getStatus() != null ? request.getStatus() : LeadStatus.PENDING;

        Lead lead = Lead.builder()
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .leadSource(request.getLeadSource())
                .notes(request.getNotes())
                .leadDate(request.getLeadDate() != null ? request.getLeadDate() : LocalDate.now())
                .ownerId(ownerId)
                .assignedAgentId(assignedAgentId)
                .status(status)
                .build();

        Lead saved = leadRepo.save(lead);
        logger.info("Lead creado: id={} status={} owner={} agente={}", saved.getId(), status, ownerId, assignedAgentId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BulkLeadResponse createBulkLeads(List<CreateLeadRequest> leads, Long ownerId, Long assignedAgentId) {
        List<Lead> toSave = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Pre-cargar lista de agentes para round-robin si aplica
        boolean isAutoRoundRobin = assignedAgentId == null &&
                "AUTO_ROUND_ROBIN".equalsIgnoreCase(configUseCases.getValue(ownerId, "leads.assignment_mode"));
        List<Long> agentIds = isAutoRoundRobin
                ? agentRepositoryPort.findByAdminId(ownerId).stream()
                        .filter(a -> Boolean.TRUE.equals(a.getActive()))
                        .map(Agent::getId).toList()
                : List.of();

        AtomicInteger bulkIndex = isAutoRoundRobin && !agentIds.isEmpty()
                ? new AtomicInteger(roundRobinIndex.computeIfAbsent(ownerId, k -> new AtomicInteger(0)).get())
                : null;

        for (int i = 0; i < leads.size(); i++) {
            CreateLeadRequest req = leads.get(i);
            try {
                if (req.getContactName() == null || req.getContactName().isBlank())
                    throw new IllegalArgumentException("Nombre requerido");
                if (req.getContactPhone() == null || req.getContactPhone().isBlank())
                    throw new IllegalArgumentException("Telefono requerido");

                Long rowAgent = req.getAssignedAgentId() != null ? req.getAssignedAgentId() : assignedAgentId;
                if (rowAgent == null && bulkIndex != null && !agentIds.isEmpty()) {
                    rowAgent = agentIds.get(bulkIndex.getAndIncrement() % agentIds.size());
                }

                LeadStatus rowStatus = req.getStatus() != null ? req.getStatus()
                        : rowAgent != null ? LeadStatus.PENDING : LeadStatus.NEW;

                Lead lead = Lead.builder()
                        .contactName(req.getContactName())
                        .contactPhone(req.getContactPhone())
                        .leadSource(req.getLeadSource())
                        .notes(req.getNotes())
                        .leadDate(req.getLeadDate() != null ? req.getLeadDate() : LocalDate.now())
                        .ownerId(ownerId)
                        .assignedAgentId(rowAgent)
                        .status(rowStatus)
                        .build();
                toSave.add(lead);
            } catch (Exception e) {
                errors.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }

        // Actualizar el índice global de round-robin
        if (bulkIndex != null && !agentIds.isEmpty()) {
            roundRobinIndex.computeIfAbsent(ownerId, k -> new AtomicInteger(0))
                    .set(bulkIndex.get() % agentIds.size());
        }

        List<Lead> saved = toSave.isEmpty() ? List.of() : leadRepo.saveAll(toSave);
        logger.info("Bulk leads: {} creados, {} errores", saved.size(), errors.size());

        return BulkLeadResponse.builder()
                .total(leads.size()).created(saved.size())
                .failed(errors.size()).errors(errors)
                .build();
    }

    @Override
    public LeadResponse getLead(Long leadId) {
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado: " + leadId));
        return toResponse(lead);
    }

    @Override
    public List<LeadResponse> listLeadsByOwner(Long ownerId, LeadStatus status, LocalDate from, LocalDate to) {
        List<Lead> leads;
        if (from != null && to != null)
            leads = leadRepo.findByOwnerAndDateRange(ownerId, from, to);
        else if (status != null)
            leads = leadRepo.findByOwnerIdAndStatus(ownerId, status);
        else
            leads = leadRepo.findByOwnerId(ownerId);
        return leads.stream().map(this::toResponse).toList();
    }

    @Override
    public List<LeadResponse> listAssignedLeads(Long userId) {
        var agentEntity = agentRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado para userId: " + userId));
        Long agentId = agentEntity.getId();

        // Si leads son públicos, el agente ve todos los leads activos del admin
        Long adminId = agentEntity.getGroup() != null ? agentEntity.getGroup().getAdminId() : null;
        boolean isPublic = adminId != null
                && configUseCases.getBooleanValue(adminId, "leads.visibility");

        // Público: leads sin agente + los propios. Pre-asignados a otro agente quedan ocultos.
        List<Lead> leads = isPublic
                ? leadRepo.findPublicLeadsForAgent(adminId, agentId)
                : leadRepo.findAssignedPendingLeads(agentId);

        return leads.stream().map(this::toResponse).toList();
    }

    @Override
    public List<LeadResponse> listPendingCallbacks(Long userId) {
        Long agentId = agentRepo.findByUserId(userId)
                .map(a -> a.getId())
                .orElse(null);
        return leadRepo.findPendingCallbacks(userId, agentId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public LeadResponse updateLead(Long leadId, CreateLeadRequest request, Long requesterId) {
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado"));
        lead.setContactName(request.getContactName());
        lead.setContactPhone(request.getContactPhone());
        lead.setLeadSource(request.getLeadSource());
        lead.setNotes(request.getNotes());
        if (request.getLeadDate() != null) lead.setLeadDate(request.getLeadDate());
        return toResponse(leadRepo.save(lead));
    }

    @Override
    @Transactional
    public LeadResponse assignLead(Long leadId, Long assignedAgentId, Long requesterId) {
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado"));
        lead.setAssignedAgentId(assignedAgentId);
        lead.setStatus(LeadStatus.PENDING);
        logger.info("Lead {} asignado al agente {}", leadId, assignedAgentId);
        return toResponse(leadRepo.save(lead));
    }

    @Override
    @Transactional
    public LeadResponse takeLead(Long leadId, Long userId) {
        var agentEntity = agentRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado para userId: " + userId));
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado"));
        lead.setAssignedAgentId(agentEntity.getId());
        lead.setStatus(LeadStatus.PENDING);
        logger.info("Lead {} tomado por agente {} (userId={})", leadId, agentEntity.getId(), userId);
        return toResponse(leadRepo.save(lead));
    }

    @Override
    @Transactional
    public void discardLead(Long leadId, Long requesterId) {
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado"));
        lead.setStatus(LeadStatus.DISCARDED);
        leadRepo.save(lead);
    }

    @Override
    @Transactional
    public LeadResponse updateLeadStatus(Long leadId, LeadStatus status, LocalDate callbackDate) {
        Lead lead = leadRepo.findById(leadId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado"));
        lead.setStatus(status);
        if (callbackDate != null) lead.setCallbackDate(callbackDate);
        return toResponse(leadRepo.save(lead));
    }

    // ── Round-robin helper ────────────────────────────────────────────────────

    /**
     * Si el modo de asignación es AUTO_ROUND_ROBIN, devuelve el siguiente agente activo.
     * Devuelve null si el modo es MANUAL o no hay agentes disponibles.
     */
    private Long resolveRoundRobinAgent(Long adminId) {
        String mode = configUseCases.getValue(adminId, "leads.assignment_mode");
        if (!"AUTO_ROUND_ROBIN".equalsIgnoreCase(mode)) return null;

        List<Long> agentIds = agentRepositoryPort.findByAdminId(adminId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .map(Agent::getId)
                .toList();
        if (agentIds.isEmpty()) return null;

        AtomicInteger idx = roundRobinIndex.computeIfAbsent(adminId, k -> new AtomicInteger(0));
        int current = idx.getAndIncrement() % agentIds.size();
        return agentIds.get(current);
    }

    private LeadResponse toResponse(Lead lead) {
        String ownerName = userRepo.findById(lead.getOwnerId())
                .map(u -> u.getName()).orElse("Desconocido");
        String assignedName = null;
        if (lead.getAssignedAgentId() != null) {
            assignedName = agentRepo.findById(lead.getAssignedAgentId())
                    .flatMap(a -> userRepo.findById(a.getUserId()))
                    .map(u -> u.getName()).orElse("Desconocido");
        }
        return LeadMapper.domainToResponse(lead, ownerName, assignedName);
    }
}
