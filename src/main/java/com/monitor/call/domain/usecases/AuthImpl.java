package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.in.AuthUseCases;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.exceptions.SupportMessages;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import com.monitor.call.infrastructure.mappers.UserMapper;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuthImpl implements AuthUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AuthImpl.class);

    private final UserRepositoryPort userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private final SupportMessages supportMessages;

    private final AgentJpaRepository agentJpaRepository;
    private final LicenseJpaRepository licenseRepo;

    public AuthImpl(UserRepositoryPort userRepo,
                    PasswordEncoder passwordEncoder,
                    JwtUtil jwtUtil,
                    SupportMessages supportMessages,
                    AgentJpaRepository agentJpaRepository,
                    LicenseJpaRepository licenseRepo) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.supportMessages = supportMessages;
        this.agentJpaRepository = agentJpaRepository;
        this.licenseRepo = licenseRepo;
    }

    @Override
    public LoginResponse login(String email, String password) {
        User user = userRepo.findByEmail(email)
                .filter(u -> u.getActive())
                .orElseThrow(() -> new RuntimeException("Credenciales invalidas"));

        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new RuntimeException("Credenciales invalidas");

        // Validar licencia (SUPER_ADMIN siempre tiene acceso)
        if (!user.getRoles().contains(Role.SUPER_ADMIN)) {
            validateLicense(user);
        }

        // Buscar extension del agente si el usuario tiene rol CALL_AGENT o SALES_AGENT
        String extension = agentJpaRepository.findByUserId(user.getId())
                .map(a -> a.getExtension())
                .orElse(null);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRoles(), extension);
        logger.info("Login exitoso: {} extension: {}", email, extension);

        return LoginResponse.builder()
                .token(token).tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMinutes() * 60)
                .userId(user.getId()).name(user.getName()).email(user.getEmail())
                .roles(user.getRoles()).extension(extension)
                .mustChangePassword(user.getMustChangePassword())
                .build();
    }

    private void validateLicense(User user) {
        Long adminId = null;

        if (user.getRoles().contains(Role.ADMIN)) {
            adminId = user.getId();
        } else {
            // Para agentes, obtener adminId desde su grupo
            AgentEntity agent = agentJpaRepository.findByUserId(user.getId()).orElse(null);
            if (agent != null && agent.getGroup() != null) {
                adminId = agent.getGroup().getAdminId();
            }
        }

        if (adminId == null) return; // sin grupo/admin configurado, permitir

        LicenseEntity license = licenseRepo.findByAdminId(adminId).orElse(null);
        if (license == null) return; // sin licencia configurada, permitir (período de gracia)

        if (license.getStatus() == LicenseStatus.PENDING) {
            logger.warn("Acceso bloqueado: licencia PENDING (sin activar) para adminId={}", adminId);
            throw new RuntimeException("LICENSE_PENDING");
        }
        if (license.getStatus() == LicenseStatus.EXPIRED) {
            logger.warn("Acceso bloqueado: licencia EXPIRED para adminId={}", adminId);
            throw new RuntimeException("LICENSE_EXPIRED");
        }
        if (license.getStatus() == LicenseStatus.SUSPENDED) {
            logger.warn("Acceso bloqueado: licencia SUSPENDED para adminId={}", adminId);
            throw new RuntimeException("LICENSE_SUSPENDED");
        }
    }

    @Override
    @Transactional
    public UserResponse registerAdmin(String name, String email, String password) {
        if (userRepo.existsByEmail(email))
            throw new RuntimeException("El email ya esta registrado");

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
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new RuntimeException("La contrasena actual es incorrecta");

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepo.save(user);
    }

    @Override
    @Transactional
    public UserResponse addRole(Long userId, Role role) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.add(role);
        user.setRoles(roles);
        return UserMapper.domainToResponse(userRepo.save(user));
    }

    @Override
    @Transactional
    public UserResponse removeRole(Long userId, Role role) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.remove(role);
        if (roles.isEmpty()) throw new RuntimeException("El usuario debe tener al menos un rol");
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
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    @Transactional
    public void deactivateUser(Long userId) {
        userRepo.deactivate(userId);
        logger.info("Usuario desactivado id: {}", userId);
    }
}
