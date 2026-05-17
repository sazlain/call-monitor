package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.exceptions.BusinessRuleException;
import com.monitor.call.domain.exceptions.ConflictException;
import com.monitor.call.domain.exceptions.ForbiddenException;
import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.exceptions.UnauthorizedException;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.License;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.in.AuthUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.LicenseRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.exceptions.SupportMessages;
import com.monitor.call.infrastructure.mappers.UserMapper;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.UUID;

@Service
public class AuthImpl implements AuthUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AuthImpl.class);

    private final UserRepositoryPort userRepo;
    private final AgentRepositoryPort agentRepo;
    private final LicenseRepositoryPort licenseRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SupportMessages supportMessages;

    public AuthImpl(UserRepositoryPort userRepo,
                    AgentRepositoryPort agentRepo,
                    LicenseRepositoryPort licenseRepo,
                    PasswordEncoder passwordEncoder,
                    JwtUtil jwtUtil,
                    SupportMessages supportMessages) {
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.licenseRepo = licenseRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.supportMessages = supportMessages;
    }

    @Override
    public LoginResponse login(String email, String password) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Credenciales invalidas"));

        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new UnauthorizedException("Credenciales invalidas");

        if (!Boolean.TRUE.equals(user.getActive())) {
            logger.warn("Intento de login con cuenta desactivada: {}", email);
            throw new ForbiddenException("ACCOUNT_DISABLED");
        }

        LicenseStatus licenseStatus = null;
        if (!user.getRoles().contains(Role.SUPER_ADMIN)) {
            licenseStatus = resolveLicenseStatus(user);
        }

        String extension = agentRepo.findByUserId(user.getId())
                .map(Agent::getExtension)
                .orElse(null);

        // ADMIN, CALL_AGENT y SALES_AGENT: sesión única — invalidar cualquier sesión anterior
        String sessionId = null;
        boolean isAgent = user.getRoles().contains(Role.CALL_AGENT)
                       || user.getRoles().contains(Role.SALES_AGENT)
                       || user.getRoles().contains(Role.ADMIN);
        if (isAgent) {
            sessionId = UUID.randomUUID().toString();
            user.setSessionId(sessionId);
            userRepo.save(user);
            logger.info("Sesión única generada para agente {}: {}", email, sessionId.substring(0, 8) + "…");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRoles(), extension, sessionId);
        logger.info("Login exitoso: {} extension: {} licenseStatus: {}", email, extension, licenseStatus);

        return LoginResponse.builder()
                .token(token).tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMinutes() * 60)
                .userId(user.getId()).name(user.getName()).email(user.getEmail())
                .roles(user.getRoles()).extension(extension)
                .mustChangePassword(user.getMustChangePassword())
                .licenseStatus(licenseStatus)
                .build();
    }

    private LicenseStatus resolveLicenseStatus(User user) {
        Long adminId = resolveAdminId(user);
        if (adminId == null) return null;

        License license = licenseRepo.findByAdminId(adminId).orElse(null);
        if (license == null) return null;

        LicenseStatus status = license.getStatus();
        boolean isBlocked = status == LicenseStatus.PENDING
                || status == LicenseStatus.TRIAL
                || status == LicenseStatus.EXPIRED
                || status == LicenseStatus.SUSPENDED;

        if (isBlocked && !user.getRoles().contains(Role.ADMIN)) {
            // Agentes y sales agents no pueden trabajar si el admin no tiene licencia activa
            logger.warn("Acceso bloqueado: licencia {} para adminId={} usuario={}", status, adminId, user.getEmail());
            throw new ForbiddenException("LICENSE_" + status.name());
        }

        return status;
    }

    private Long resolveAdminId(User user) {
        if (user.getRoles().contains(Role.ADMIN)) {
            return user.getId();
        }
        // SALES_AGENT guarda adminId directamente en la entidad User
        if (user.getRoles().contains(Role.SALES_AGENT) && user.getAdminId() != null) {
            return user.getAdminId();
        }
        // CALL_AGENT resuelve adminId a través de su registro de agente
        return agentRepo.findByUserId(user.getId())
                .map(Agent::getAdminId)
                .orElse(null);
    }

    @Override
    @Transactional
    public UserResponse registerAdmin(String name, String email, String password) {
        if (userRepo.existsByEmail(email))
            throw new ConflictException("El email ya esta registrado");

        User admin = User.builder()
                .name(name).email(email)
                .password(passwordEncoder.encode(password))
                .active(true).roles(Set.of(Role.ADMIN)).mustChangePassword(false)
                .build();

        logger.info("Admin registrado: {}", email);
        return UserMapper.domainToResponse(userRepo.save(admin));
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new UnauthorizedException("La contrasena actual es incorrecta");

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepo.save(user);
    }

    @Override
    @Transactional
    public UserResponse addRole(Long userId, Role role) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.add(role);
        user.setRoles(roles);
        return UserMapper.domainToResponse(userRepo.save(user));
    }

    @Override
    @Transactional
    public UserResponse removeRole(Long userId, Role role) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.remove(role);
        if (roles.isEmpty()) throw new BusinessRuleException("El usuario debe tener al menos un rol");
        user.setRoles(roles);
        return UserMapper.domainToResponse(userRepo.save(user));
    }

    @Override
    public List<UserResponse> listUsers() {
        return userRepo.findAll().stream().map(UserMapper::domainToResponse).toList();
    }

    @Override
    public UserResponse getUserById(Long userId) {
        return userRepo.findById(userId).map(UserMapper::domainToResponse)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    @Override
    @Transactional
    public void deactivateUser(Long userId) {
        userRepo.deactivate(userId);
        logger.info("Usuario desactivado id: {}", userId);
    }
}
