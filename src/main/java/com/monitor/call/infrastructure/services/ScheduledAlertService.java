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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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
    private final ScheduleUseCases scheduleService;

    public ScheduledAlertService(UserRepositoryPort userRepo,
                                  AgentRepositoryPort agentRepo,
                                  DashboardRepositoryPort dashboardRepo,
                                  LeadRepositoryPort leadRepo,
                                  SystemConfigUseCases configUseCases,
                                  AgentGoalUseCases goalUseCases,
                                  EmailService emailService,
                                  ScheduleUseCases scheduleService) {
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.dashboardRepo = dashboardRepo;
        this.leadRepo = leadRepo;
        this.configUseCases = configUseCases;
        this.goalUseCases = goalUseCases;
        this.emailService = emailService;
        this.scheduleService = scheduleService;
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

                String html = EmailService.wrap(
                        "⏱ Agentes inactivos",
                        "<p>Los siguientes agentes llevan más de <strong>" + threshold +
                        " minutos</strong> sin actividad:</p>" +
                        "<p style='font-size:15px;font-weight:bold;color:#dc2626;'>" + agentNames + "</p>" +
                        "<p style='color:#6b7280;font-size:13px;'>Hora de verificación: " +
                        OffsetDateTime.now() + "</p>");

                emailService.send(admin.getEmail(),
                        "Alerta: agentes inactivos (" + idleExts.size() + ")", html);
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

            StringBuilder rows = new StringBuilder();
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
                rows.append(EmailService.row(agentName, calls + " llamadas / " + answered + " contestadas"));
                totalCalls += calls;
                totalAnswered += answered;
            }

            long durationMinutes = totalDuration != null ? (long) (totalDuration / 60) : 0;

            String html = EmailService.wrap(
                    "📊 Resumen del día",
                    "<p>Actividad del día para su equipo:</p>" +
                    EmailService.table(rows.toString()) +
                    EmailService.table(
                            EmailService.row("Total llamadas", String.valueOf(totalCalls)),
                            EmailService.row("Llamadas contestadas", String.valueOf(totalAnswered)),
                            EmailService.row("Tiempo total en llamadas", durationMinutes + " min")));

            emailService.send(admin.getEmail(), "Resumen diario — Voxio", html);
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

            StringBuilder rows = new StringBuilder();
            for (AgentGoalHistoryResponse h : adminUnmet) {
                String label = h.getAgentName() + " — " + h.getKpiType() + " (" + h.getPeriod() + ")";
                String value = h.getActualValue() + " / " + h.getTargetValue() +
                               " (" + h.getProgressPercent() + "%)";
                rows.append(EmailService.row(label, value));
            }

            String html = EmailService.wrap(
                    "🎯 Metas no cumplidas hoy",
                    "<p>Los siguientes agentes no alcanzaron su meta del día:</p>" +
                    EmailService.table(rows.toString()));

            emailService.send(admin.getEmail(),
                    "Alerta: " + adminUnmet.size() + " metas sin cumplir", html);
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

            StringBuilder rows = new StringBuilder();
            for (Lead lead : callbacks) {
                String agentName = agents.stream()
                        .filter(a -> a.getId().equals(lead.getAssignedAgentId()))
                        .map(a -> a.getUserName() != null ? a.getUserName() : "Agente " + a.getId())
                        .findFirst().orElse("—");
                String date = lead.getCallbackDate() != null ? lead.getCallbackDate().toString() : "—";
                rows.append(EmailService.row(lead.getContactName() + " (" + lead.getContactPhone() + ")",
                        "Agente: " + agentName + " | Fecha callback: " + date));
            }

            String html = EmailService.wrap(
                    "📞 Callbacks pendientes",
                    "<p>Hay <strong>" + callbacks.size() + "</strong> callbacks pendientes para hoy:</p>" +
                    EmailService.table(rows.toString()));

            emailService.send(admin.getEmail(),
                    "Callbacks pendientes: " + callbacks.size(), html);
            logger.info("Recordatorio callbacks enviado a admin={} cantidad={}", admin.getId(), callbacks.size());
        }
    }
}
