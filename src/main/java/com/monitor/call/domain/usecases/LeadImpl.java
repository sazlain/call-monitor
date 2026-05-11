package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.LeadUseCases;
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
import java.util.stream.Collectors;

@Service
public class LeadImpl implements LeadUseCases {

    private static final Logger logger = LoggerFactory.getLogger(LeadImpl.class);

    private final LeadRepositoryPort leadRepo;
    private final UserJpaRepository userRepo;
    private final AgentJpaRepository agentRepo;

    public LeadImpl(LeadRepositoryPort leadRepo, UserJpaRepository userRepo, AgentJpaRepository agentRepo) {
        this.leadRepo = leadRepo;
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
    }

    @Override
    @Transactional
    public LeadResponse createLead(CreateLeadRequest request, Long ownerId) {
        Lead lead = Lead.builder()
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .leadSource(request.getLeadSource())
                .notes(request.getNotes())
                .leadDate(request.getLeadDate() != null ? request.getLeadDate() : LocalDate.now())
                .ownerId(ownerId)
                .assignedAgentId(request.getAssignedAgentId())
                .status(request.getAssignedAgentId() != null ? LeadStatus.PENDING : LeadStatus.NEW)
                .build();

        Lead saved = leadRepo.save(lead);
        logger.info("Lead creado: {} por owner {}", saved.getId(), ownerId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BulkLeadResponse createBulkLeads(List<CreateLeadRequest> leads, Long ownerId, Long assignedAgentId) {
        List<Lead> toSave = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < leads.size(); i++) {
            CreateLeadRequest req = leads.get(i);
            try {
                if (req.getContactName() == null || req.getContactName().isBlank())
                    throw new IllegalArgumentException("Nombre requerido");
                if (req.getContactPhone() == null || req.getContactPhone().isBlank())
                    throw new IllegalArgumentException("Telefono requerido");

                Lead lead = Lead.builder()
                        .contactName(req.getContactName())
                        .contactPhone(req.getContactPhone())
                        .leadSource(req.getLeadSource())
                        .notes(req.getNotes())
                        .leadDate(req.getLeadDate() != null ? req.getLeadDate() : LocalDate.now())
                        .ownerId(ownerId)
                        .assignedAgentId(assignedAgentId)
                        .status(assignedAgentId != null ? LeadStatus.PENDING : LeadStatus.NEW)
                        .build();
                toSave.add(lead);
            } catch (Exception e) {
                errors.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
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
        // Convertir userId → agentId (leads.assigned_agent_id referencia agents.id)
        Long agentId = agentRepo.findByUserId(userId)
                .map(a -> a.getId())
                .orElseThrow(() -> new RuntimeException("Agente no encontrado para userId: " + userId));
        return leadRepo.findAssignedPendingLeads(agentId).stream()
                .map(this::toResponse)
                .toList();
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
