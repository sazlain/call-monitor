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

        if (!user.getRoles().contains(Role.SUPER_ADMIN)) {
            validateLicense(user);
        }

        String extension = agentRepo.findByUserId(user.getId())
                .map(Agent::getExtension)
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
        Long adminId = resolveAdminId(user);
        if (adminId == null) return;

        License license = licenseRepo.findByAdminId(adminId).orElse(null);
        if (license == null) return;

        if (license.getStatus() == LicenseStatus.PENDING) {
            logger.warn("Acceso bloqueado: licencia PENDING para adminId={}", adminId);
            throw new ForbiddenException("LICENSE_PENDING");
        }
        if (license.getStatus() == LicenseStatus.EXPIRED) {
            logger.warn("Acceso bloqueado: licencia EXPIRED para adminId={}", adminId);
            throw new ForbiddenException("LICENSE_EXPIRED");
        }
        if (license.getStatus() == LicenseStatus.SUSPENDED) {
            logger.warn("Acceso bloqueado: licencia SUSPENDED para adminId={}", adminId);
            throw new ForbiddenException("LICENSE_SUSPENDED");
        }
    }

    private Long resolveAdminId(User user) {
        if (user.getRoles().contains(Role.ADMIN)) {
            return user.getId();
        }
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
