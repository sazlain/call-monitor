package com.monitor.call.infrastructure.services;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.ports.in.ScheduleUseCases;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.in.AgentGoalUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.DashboardRepositoryPort;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.AgentGoalHistoryResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.PushSubscriptionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de alertas programadas.
 * Todos los métodos iteran sobre los admins registrados para mantener el aislamiento multi-tenant.
 */
@Service
public class ScheduledAlertService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledAlertService.class);

    private final UserRepositoryPort userRepo;
    private final AgentRepositoryPort agentRepo;
    private final DashboardRepositoryPort dashboardRepo;
    private final LeadRepositoryPort leadRepo;
    private final SystemConfigUseCases configUseCases;
    private final AgentGoalUseCases goalUseCases;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;
    private final ScheduleUseCases scheduleService;
    private final PushNotificationService pushService;
    private final PushSubscriptionJpaRepository pushRepo;

    public ScheduledAlertService(UserRepositoryPort userRepo,
                                  AgentRepositoryPort agentRepo,
                                  DashboardRepositoryPort dashboardRepo,
                                  LeadRepositoryPort leadRepo,
                                  SystemConfigUseCases configUseCases,
                                  AgentGoalUseCases goalUseCases,
                                  EmailService emailService,
                                  EmailTemplates emailTemplates,
                                  ScheduleUseCases scheduleService,
                                  PushNotificationService pushService,
                                  PushSubscriptionJpaRepository pushRepo) {
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.dashboardRepo = dashboardRepo;
        this.leadRepo = leadRepo;
        this.configUseCases = configUseCases;
        this.goalUseCases = goalUseCases;
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
        this.scheduleService = scheduleService;
        this.pushService = pushService;
        this.pushRepo = pushRepo;
    }

    // ── 1. Inactividad de agentes — cada 5 minutos ─────────────────────────────

    @Scheduled(cron = "0 */5 * * * *")
    public void checkIdleAgents() {
        List<User> admins = userRepo.findByRole(Role.ADMIN);
        OffsetDateTime now = OffsetDateTime.now();
        for (User admin : admins) {
            if (!configUseCases.getBooleanValue(admin.getId(), "alerts.idle_enabled")) continue;
            // No alertar si el horario configurado indica que los agentes no deben estar trabajando ahora
            if (!scheduleService.isWithinSchedule(admin.getId(), now)) continue;
            int threshold = configUseCases.getIntValue(admin.getId(), "alerts.idle_threshold_minutes");
            if (threshold <= 0) continue;

            List<Agent> agents = agentRepo.findByAdminId(admin.getId());
            List<String> extensions = agents.stream().map(Agent::getExtension).toList();
            if (extensions.isEmpty()) continue;

            OffsetDateTime since = OffsetDateTime.now().minusMinutes(threshold);
            List<String> idleExts = dashboardRepo.findInactiveExtensions(extensions, since);

            if (!idleExts.isEmpty()) {
                String agentNames = agents.stream()
                        .filter(a -> idleExts.contains(a.getExtension()))
                        .map(a -> a.getUserName() != null ? a.getUserName() : a.getExtension())
                        .reduce((a, b) -> a + ", " + b).orElse(String.join(", ", idleExts));

                String html = emailTemplates.idleAgentsAlert(
                        agentNames, threshold, OffsetDateTime.now().toString());

                emailService.send(admin.getEmail(),
                        "Alerta: agentes inactivos (" + idleExts.size() + ")", html);
                pushService.sendToAll(pushRepo.findByUserId(admin.getId()),
                        "Agentes inactivos (" + idleExts.size() + ")",
                        agentNames + " sin actividad por " + threshold + " min",
                        "/pwa-192x192.png", "/dashboard");
                logger.info("Alerta de inactividad enviada a admin={} agentes={}", admin.getId(), idleExts.size());
            }
        }
    }

    // ── 2. Resumen diario — cada día a las 18:00, respetando el horario del admin ──

    @Scheduled(cron = "0 0 18 * * *")
    public void sendDailySummary() {
        List<User> admins = userRepo.findByRole(Role.ADMIN);
        OffsetDateTime now  = OffsetDateTime.now();
        OffsetDateTime from = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime to   = now;

        for (User admin : admins) {
            if (!configUseCases.getBooleanValue(admin.getId(), "alerts.daily_summary")) continue;
            // Solo enviar si hoy es un día laboral según la config del admin
            if (!scheduleService.isWorkDay(admin.getId(), now.getDayOfWeek())) continue;

            List<Agent> agents = agentRepo.findByAdminId(admin.getId());
            if (agents.isEmpty()) continue;

            List<String> extensions = agents.stream().map(Agent::getExtension).toList();
            List<Object[]> summary = dashboardRepo.getCallSummaryByExtensions(extensions, from, to);
            Double totalDuration = dashboardRepo.sumDurationByExtensions(extensions, from, to);

            List<String[]> agentRows = new ArrayList<>();
            long totalCalls = 0;
            long totalAnswered = 0;
            for (Object[] row : summary) {
                String ext = String.valueOf(row[0]);
                long calls = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0;
                long answered = row[2] instanceof Number ? ((Number) row[2]).longValue() : 0;
                String agentName = agents.stream()
                        .filter(a -> ext.equals(a.getExtension()))
                        .map(a -> a.getUserName() != null ? a.getUserName() : ext)
                        .findFirst().orElse(ext);
                agentRows.add(new String[]{agentName, String.valueOf(calls), String.valueOf(answered)});
                totalCalls += calls;
                totalAnswered += answered;
            }

            long durationMinutes = totalDuration != null ? (long) (totalDuration / 60) : 0;
            String date = LocalDate.now().toString();

            String html = emailTemplates.dailySummary(date, agentRows, totalCalls, totalAnswered, durationMinutes);

            emailService.send(admin.getEmail(), "Resumen diario — ZentCall", html);
            pushService.sendToAll(pushRepo.findByUserId(admin.getId()),
                    "Resumen del día",
                    totalCalls + " llamadas — " + totalAnswered + " contestadas en " + durationMinutes + " min",
                    "/pwa-192x192.png", "/dashboard/admin");
            logger.info("Resumen diario enviado a admin={}", admin.getId());
        }
    }

    // ── 3. Evaluación de metas al cierre del día — 23:59 ─────────────────────

    @Scheduled(cron = "0 59 23 * * *")
    public void evaluateGoalsEndOfDay() {
        List<AgentGoalHistoryResponse> unmet = goalUseCases.evaluateDailyGoals();
        if (unmet.isEmpty()) return;

        List<User> admins = userRepo.findByRole(Role.ADMIN);
        OffsetDateTime now = OffsetDateTime.now();
        for (User admin : admins) {
            if (!configUseCases.getBooleanValue(admin.getId(), "alerts.goal_not_met")) continue;
            // No evaluar metas si hoy no es día laboral para este admin
            if (!scheduleService.isWorkDay(admin.getId(), now.getDayOfWeek())) continue;

            List<AgentGoalHistoryResponse> adminUnmet = unmet.stream()
                    .filter(h -> {
                        // Check if this agent belongs to this admin
                        Agent agent = agentRepo.findById(h.getAgentId()).orElse(null);
                        return agent != null && admin.getId().equals(agent.getAdminId());
                    })
                    .toList();

            if (adminUnmet.isEmpty()) continue;

            List<String[]> goalRows = new ArrayList<>();
            for (AgentGoalHistoryResponse h : adminUnmet) {
                goalRows.add(new String[]{
                    h.getAgentName(),
                    h.getKpiType() != null ? h.getKpiType().name() : "—",
                    h.getPeriod() != null ? h.getPeriod().name() : "—",
                    h.getActualValue() != null ? h.getActualValue().toString() : "0",
                    h.getTargetValue() != null ? h.getTargetValue().toString() : "0",
                    h.getProgressPercent() != null ? h.getProgressPercent().toString() : "0"
                });
            }

            String goalDate = LocalDate.now().toString();
            String html = emailTemplates.goalsNotMet(goalDate, goalRows);

            emailService.send(admin.getEmail(),
                    "Alerta: " + adminUnmet.size() + " metas sin cumplir", html);
            pushService.sendToAll(pushRepo.findByUserId(admin.getId()),
                    "Metas sin cumplir: " + adminUnmet.size(),
                    "Revisa el rendimiento de tus agentes",
                    "/pwa-192x192.png", "/admin/goals");
            logger.info("Alerta metas no cumplidas enviada a admin={} cantidad={}", admin.getId(), adminUnmet.size());
        }
    }

    // ── 4. Recordatorio de callbacks pendientes — 9:00 AM ─────────────────────

    @Scheduled(cron = "0 0 9 * * *")
    public void remindPendingCallbacks() {
        List<User> admins = userRepo.findByRole(Role.ADMIN);
        for (User admin : admins) {
            if (!configUseCases.getBooleanValue(admin.getId(), "alerts.callback_reminder")) continue;

            List<Agent> agents = agentRepo.findByAdminId(admin.getId());
            if (agents.isEmpty()) continue;

            List<Lead> callbacks = agents.stream()
                    .flatMap(a -> leadRepo.findPendingCallbacks(admin.getId(), a.getId()).stream())
                    .distinct()
                    .toList();

            if (callbacks.isEmpty()) continue;

            List<String[]> callbackRows = new ArrayList<>();
            for (Lead lead : callbacks) {
                String agentName = agents.stream()
                        .filter(a -> a.getId().equals(lead.getAssignedAgentId()))
                        .map(a -> a.getUserName() != null ? a.getUserName() : "Agente " + a.getId())
                        .findFirst().orElse("—");
                String cbDate = lead.getCallbackDate() != null ? lead.getCallbackDate().toString() : "—";
                callbackRows.add(new String[]{
                    lead.getContactName() != null ? lead.getContactName() : "—",
                    lead.getContactPhone() != null ? lead.getContactPhone() : "—",
                    agentName,
                    cbDate
                });
            }

            String cbDateStr = LocalDate.now().toString();
            String html = emailTemplates.pendingCallbacks(cbDateStr, callbackRows);

            emailService.send(admin.getEmail(),
                    "Callbacks pendientes: " + callbacks.size(), html);
            pushService.sendToAll(pushRepo.findByUserId(admin.getId()),
                    "Callbacks pendientes: " + callbacks.size(),
                    "Hay leads esperando ser contactados hoy",
                    "/pwa-192x192.png", "/leads");
            logger.info("Recordatorio callbacks enviado a admin={} cantidad={}", admin.getId(), callbacks.size());
        }
    }
}
