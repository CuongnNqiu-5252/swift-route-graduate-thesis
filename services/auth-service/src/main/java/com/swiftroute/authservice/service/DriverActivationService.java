package com.swiftroute.authservice.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftroute.authservice.config.JwtConfig;
import com.swiftroute.authservice.dto.request.DriverActivateRequestDTO;
import com.swiftroute.authservice.dto.response.DriverActivationAuthResponseDTO;
import com.swiftroute.authservice.dto.response.UserProfileResponseDTO;
import com.swiftroute.authservice.entity.DriverActivationKey;
import com.swiftroute.authservice.entity.RevokedAuthSession;
import com.swiftroute.authservice.entity.TenantMembership;
import com.swiftroute.authservice.entity.User;
import com.swiftroute.authservice.entity.enums.Role;
import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;
import com.swiftroute.authservice.entity.enums.TenantPermission;
import com.swiftroute.authservice.exception.AppException;
import com.swiftroute.authservice.exception.ErrorCode;
import com.swiftroute.authservice.repository.DriverActivationKeyRepository;
import com.swiftroute.authservice.repository.RefreshTokenRepository;
import com.swiftroute.authservice.repository.RevokedAuthSessionRepository;
import com.swiftroute.authservice.repository.TenantMembershipRepository;
import com.swiftroute.authservice.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DriverActivationService {

    public static final String ACTIVATION_EMAIL_EVENT_TYPE = "tenant.driver_activation_code.v1";
    private static final String SYNTHETIC_EMAIL_DOMAIN = "@driver.swiftroute.internal";
    private static final long DEFAULT_KEY_EXPIRY_HOURS = 24L;
    private static final int MAX_ATTEMPTS_PER_IP_PER_MINUTE = 10;
    private static final int MAX_ATTEMPTS_PER_DEVICE_PER_MINUTE = 8;
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final DateTimeFormatter VN_EXPIRY_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final DriverActivationKeyRepository activationKeyRepository;
    private final RevokedAuthSessionRepository revokedSessionRepository;
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final DriverOnboardingClient driverOnboardingClient;
    private final TenantDetailsClient tenantDetailsClient;
    private final VerificationCipher cipher;
    private final JdbcTemplate jdbcTemplate;
    private final TenantPermissionService tenantPermissionService;
    private final String hmacSecret;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, Instant> revokedSessionCache = new ConcurrentHashMap<>();
    private final Map<String, RateBucket> ipRateBuckets = new ConcurrentHashMap<>();
    private final Map<String, RateBucket> deviceRateBuckets = new ConcurrentHashMap<>();

    private record RateBucket(long windowStartMs, AtomicInteger count) {
    }

    public record IssueKeyCommand(
            UUID tenantId,
            UUID driverId,
            UUID existingUserId,
            String fullName,
            String phone,
            String email,
            UUID createdBy,
            UUID revokeSessionId,
            boolean sendEmail) {
    }

    public record IssuedKeyResult(
            UUID driverId,
            UUID userId,
            String activationKey,
            String formattedActivationKey,
            Instant expiresAt,
            boolean emailQueued) {
    }

    public record KeyStateSnapshot(
            UUID driverId,
            String keyState,
            Instant expiresAt,
            Instant createdAt,
            Instant usedAt,
            Instant revokedAt) {
    }

    public DriverActivationService(
            DriverActivationKeyRepository activationKeyRepository,
            RevokedAuthSessionRepository revokedSessionRepository,
            UserRepository userRepository,
            TenantMembershipRepository tenantMembershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtConfig jwtConfig,
            DriverOnboardingClient driverOnboardingClient,
            TenantDetailsClient tenantDetailsClient,
            VerificationCipher cipher,
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Autowired(required = false) TenantPermissionService tenantPermissionService,
            @Value("${jwt.secret:your_super_secret_key_change_in_production}") String hmacSecret) {
        this.activationKeyRepository = activationKeyRepository;
        this.revokedSessionRepository = revokedSessionRepository;
        this.userRepository = userRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
        this.driverOnboardingClient = driverOnboardingClient;
        this.tenantDetailsClient = tenantDetailsClient;
        this.cipher = cipher;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantPermissionService = tenantPermissionService;
        this.hmacSecret = hmacSecret;
    }

    /**
     * Generates a single-use 12-digit numeric activation code (24h expiry),
     * revoking any prior unused key for this driver and optionally revoking an existing device session.
     */
    @Transactional
    public IssuedKeyResult issueActivationKey(IssueKeyCommand command) {
        UUID tenantId = command.tenantId();
        UUID driverId = command.driverId();
        if (jdbcTemplate != null) {
            long lockKey = ((long) tenantId.hashCode() << 32) ^ (driverId.hashCode() & 0xffffffffL);
            jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
        }

        User driverUser = ensureDriverUserAndMembership(
                tenantId,
                driverId,
                command.existingUserId(),
                command.fullName(),
                command.phone(),
                command.email());

        Instant now = Instant.now();
        if (command.revokeSessionId() != null) {
            revokeSingleSession(tenantId, driverId, driverUser.getId(), command.revokeSessionId(), now);
        }

        activationKeyRepository.revokeUnusedByTenantAndDriver(tenantId, driverId, now);
        activationKeyRepository.flush();

        String rawKey = generateUniqueTwelveDigitKey();
        String keyHash = hmacSha256Hex(rawKey);
        Instant expiresAt = now.plus(DEFAULT_KEY_EXPIRY_HOURS, ChronoUnit.HOURS);

        DriverActivationKey entity = DriverActivationKey.builder()
                .tenantId(tenantId)
                .driverId(driverId)
                .user(driverUser)
                .keyHash(keyHash)
                .expiresAt(expiresAt)
                .createdBy(command.createdBy())
                .createdAt(now)
                .build();
        activationKeyRepository.save(entity);

        String formattedKey = formatTwelveDigitKey(rawKey);
        String deliveryEmail = normalizeExternalEmail(command.email());
        if (deliveryEmail == null) {
            deliveryEmail = normalizeExternalEmail(driverUser.getEmail());
        }

        boolean emailQueued = false;
        if (command.sendEmail() && deliveryEmail != null) {
            enqueueActivationCodeEmail(
                    tenantId,
                    driverUser.getId(),
                    deliveryEmail,
                    command.fullName() != null && !command.fullName().isBlank() ? command.fullName().trim() : driverUser.getFullName(),
                    formattedKey,
                    expiresAt);
            emailQueued = true;
        }

        log.info("Issued driver activation key activationId={} for driverId={} in tenantId={} (emailQueued={})",
                entity.getId(), driverId, tenantId, emailQueued);

        return new IssuedKeyResult(
                driverId,
                driverUser.getId(),
                rawKey,
                formattedKey,
                expiresAt,
                emailQueued);
    }

    /**
     * Validates a 12-digit activation code from the Flutter Driver App, registers the single active device,
     * consumes the code atomically under pessimistic lock, and issues standard JWT Access + Refresh tokens.
     */
    @Transactional
    public DriverActivationAuthResponseDTO activateDriverApp(
            DriverActivateRequestDTO request,
            HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        String deviceId = request.deviceId().trim();
        enforceActivationRateLimit(clientIp, deviceId);

        String normalizedKey = normalizeTwelveDigitKey(request.activationKey());
        if (normalizedKey == null) {
            throw new AppException(ErrorCode.INVALID_ACTIVATION_KEY);
        }

        String keyHash = hmacSha256Hex(normalizedKey);
        DriverActivationKey activationKey = activationKeyRepository.findLockedByKeyHash(keyHash)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_ACTIVATION_KEY));

        Instant now = Instant.now();
        if (activationKey.getUsedAt() != null) {
            throw new AppException(ErrorCode.ACTIVATION_KEY_ALREADY_USED);
        }
        if (activationKey.getRevokedAt() != null) {
            throw new AppException(ErrorCode.ACTIVATION_KEY_REVOKED);
        }
        if (!activationKey.getExpiresAt().isAfter(now)) {
            throw new AppException(ErrorCode.ACTIVATION_KEY_EXPIRED);
        }

        User user = activationKey.getUser();
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus()) || !user.isEmailVerified()) {
            throw new AppException(ErrorCode.DRIVER_NOT_ACTIVE);
        }

        UUID tenantId = activationKey.getTenantId();
        UUID driverId = activationKey.getDriverId();

        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_ACTIVE));
        if (membership.getStatus() != TenantMembershipStatus.ACTIVE
                || membership.getMembershipRole() != TenantMembershipRole.DRIVER) {
            throw new AppException(ErrorCode.DRIVER_NOT_ACTIVE);
        }

        UUID sessionId = UUID.randomUUID();
        UUID replacedSessionId;
        try {
            replacedSessionId = driverOnboardingClient.activateDevice(
                    tenantId,
                    driverId,
                    new DriverOnboardingClient.ActivateDevicePayload(
                            user.getId(),
                            deviceId,
                            sessionId,
                            sanitizeOptional(request.deviceOs(), 30),
                            sanitizeOptional(request.osVersion(), 50),
                            sanitizeOptional(request.appVersion(), 50),
                            sanitizeOptional(request.deviceModel(), 120)));
        } catch (RestClientResponseException ex) {
            log.warn("Driver device activation rejected by driver-service for driverId={} in tenantId={} (status={})",
                    driverId, tenantId, ex.getStatusCode().value());
            throw new AppException(ErrorCode.DRIVER_NOT_ACTIVE);
        }

        if (replacedSessionId != null && !replacedSessionId.equals(sessionId)) {
            revokeSingleSession(tenantId, driverId, user.getId(), replacedSessionId, now);
        }

        activationKey.setUsedAt(now);
        activationKeyRepository.save(activationKey);

        List<String> effectivePermissions = tenantPermissionService != null
                ? tenantPermissionService.resolveEffectivePermissions(user.getRole(), membership)
                : TenantPermission.defaultPermissionsForRoleName(TenantMembershipRole.DRIVER.name(), user.getRole());
        Long permissionsVersion = membership.getPermissionsVersion() != null ? membership.getPermissionsVersion() : 1L;
        long ttlMinutes = (jwtConfig != null && jwtConfig.getAccessTokenExpiryMinutes() > 0)
                ? jwtConfig.getAccessTokenExpiryMinutes()
                : 15L;

        String accessToken = jwtService.generateTenantAccessToken(
                user,
                tenantId,
                TenantMembershipRole.DRIVER.name(),
                effectivePermissions,
                permissionsVersion,
                sessionId,
                ttlMinutes);
        String refreshToken = jwtService.generateAndStoreRefreshToken(user, httpRequest, tenantId, sessionId);

        UserProfileResponseDTO profile = new UserProfileResponseDTO(
                user.getId(),
                isSyntheticEmail(user.getEmail()) ? "" : user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getRole(),
                TenantMembershipRole.DRIVER.name(),
                tenantId,
                user.getStatus(),
                user.getCreatedAt(),
                user.isEmailVerified(),
                effectivePermissions);

        log.info("Activated mobile device sessionId={} for driverId={} userId={} in tenantId={}",
                sessionId, driverId, user.getId(), tenantId);

        return DriverActivationAuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .driverId(driverId)
                .tenantId(tenantId)
                .user(profile)
                .build();
    }

    /**
     * Revokes pending activation keys and revokes specified device sessions without affecting unrelated sessions.
     */
    @Transactional
    public void deactivateDrivers(UUID tenantId, List<UUID> driverIds, List<SessionRevokeTarget> sessionTargets) {
        Instant now = Instant.now();
        if (driverIds != null) {
            for (UUID driverId : driverIds) {
                if (driverId != null) {
                    activationKeyRepository.revokeUnusedByTenantAndDriver(tenantId, driverId, now);
                }
            }
        }
        if (sessionTargets != null) {
            for (SessionRevokeTarget target : sessionTargets) {
                if (target != null && target.sessionId() != null && target.userId() != null) {
                    revokeSingleSession(tenantId, target.driverId(), target.userId(), target.sessionId(), now);
                }
            }
        }
    }

    public record SessionRevokeTarget(UUID driverId, UUID userId, UUID sessionId) {
    }

    @Transactional(readOnly = true)
    public Map<UUID, KeyStateSnapshot> getLatestKeyStates(UUID tenantId, Collection<UUID> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return Map.of();
        }
        Instant now = Instant.now();
        List<DriverActivationKey> keys = activationKeyRepository
                .findByTenantIdAndDriverIdInOrderByCreatedAtDesc(tenantId, driverIds);
        Map<UUID, KeyStateSnapshot> result = new LinkedHashMap<>();
        for (DriverActivationKey key : keys) {
            if (result.containsKey(key.getDriverId())) {
                continue;
            }
            String state;
            if (key.getUsedAt() != null) {
                state = "USED";
            } else if (key.getRevokedAt() != null) {
                state = "REVOKED";
            } else if (key.getExpiresAt().isAfter(now)) {
                state = "KEY_CREATED";
            } else {
                state = "KEY_EXPIRED";
            }
            result.put(key.getDriverId(), new KeyStateSnapshot(
                    key.getDriverId(),
                    state,
                    key.getExpiresAt(),
                    key.getCreatedAt(),
                    key.getUsedAt(),
                    key.getRevokedAt()));
        }
        return result;
    }

    public boolean isSessionRevoked(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        Instant cachedExpiry = revokedSessionCache.get(sessionId);
        if (cachedExpiry != null) {
            if (cachedExpiry.isAfter(Instant.now())) {
                return true;
            }
            revokedSessionCache.remove(sessionId);
        }
        boolean existsInDb = revokedSessionRepository.existsBySessionId(sessionId);
        if (existsInDb) {
            revokedSessionCache.put(sessionId, Instant.now().plus(2, ChronoUnit.HOURS));
        }
        return existsInDb;
    }

    private void revokeSingleSession(UUID tenantId, UUID driverId, UUID userId, UUID sessionId, Instant now) {
        refreshTokenRepository.revokeAllBySessionId(sessionId);
        long ttlMinutes = (jwtConfig != null && jwtConfig.getAccessTokenExpiryMinutes() > 0)
                ? jwtConfig.getAccessTokenExpiryMinutes()
                : 15L;
        Instant expiresAt = now.plus(Math.max(ttlMinutes + 15L, 120L), ChronoUnit.MINUTES);
        revokedSessionCache.put(sessionId, expiresAt);
        if (!revokedSessionRepository.existsBySessionId(sessionId)) {
            revokedSessionRepository.save(RevokedAuthSession.builder()
                    .sessionId(sessionId)
                    .tenantId(tenantId)
                    .driverId(driverId)
                    .userId(userId)
                    .revokedAt(now)
                    .expiresAt(expiresAt)
                    .build());
        }
        enqueueSessionRevokedOutbox(tenantId, userId, sessionId, expiresAt);
    }

    private User ensureDriverUserAndMembership(
            UUID tenantId,
            UUID driverId,
            UUID existingUserId,
            String fullName,
            String phone,
            String rawEmail) {
        String normalizedEmail = normalizeExternalEmail(rawEmail);
        String resolvedName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Tài xế SwiftRoute";
        String resolvedPhone = (phone != null && !phone.isBlank()) ? phone.trim() : null;

        User user = null;
        if (existingUserId != null) {
            user = userRepository.findById(existingUserId).orElse(null);
        }

        if (user != null) {
            if ("SUSPENDED".equalsIgnoreCase(user.getStatus())) {
                throw new AppException(ErrorCode.DRIVER_NOT_ACTIVE);
            }
            boolean dirty = false;
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                user.setStatus("ACTIVE");
                dirty = true;
            }
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
                dirty = true;
            }
            if (normalizedEmail != null && isSyntheticEmail(user.getEmail())
                    && userRepository.findByEmail(normalizedEmail).isEmpty()) {
                user.setEmail(normalizedEmail);
                dirty = true;
            }
            if (dirty) {
                user = userRepository.save(user);
            }
        } else {
            if (normalizedEmail != null) {
                User byEmail = userRepository.findByEmail(normalizedEmail).orElse(null);
                if (byEmail != null) {
                    if (byEmail.getRole() == Role.SYSTEM_ADMIN || "SUSPENDED".equalsIgnoreCase(byEmail.getStatus())) {
                        throw new AppException(ErrorCode.USER_EXISTED);
                    }
                    boolean conflictInOtherTenant = tenantMembershipRepository
                            .findByUser_IdAndStatus(byEmail.getId(), TenantMembershipStatus.ACTIVE)
                            .stream()
                            .anyMatch(m -> (m.getMembershipRole() == TenantMembershipRole.DRIVER
                                    && !m.getTenantId().equals(tenantId))
                                    || (m.getTenantId().equals(tenantId)
                                            && m.getMembershipRole() != TenantMembershipRole.DRIVER));
                    if (conflictInOtherTenant) {
                        throw new AppException(ErrorCode.TENANT_MEMBERSHIP_ALREADY_EXISTS,
                                "Email này đang được sử dụng bởi một tài khoản khác.");
                    }
                    byEmail.setEmailVerified(true);
                    byEmail.setStatus("ACTIVE");
                    user = userRepository.save(byEmail);
                }
            }

            if (user == null) {
                String accountEmail = normalizedEmail != null
                        ? normalizedEmail
                        : ("driver+" + driverId + SYNTHETIC_EMAIL_DOMAIN);
                byte[] randomSecret = new byte[32];
                secureRandom.nextBytes(randomSecret);
                String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomSecret);
                user = User.builder()
                        .email(accountEmail)
                        .passwordHash(passwordEncoder.encode(randomPassword))
                        .fullName(resolvedName)
                        .phone(resolvedPhone)
                        .role(Role.USER)
                        .status("ACTIVE")
                        .emailVerified(true)
                        .build();
                user = userRepository.save(user);
            }
        }

        final User linkedUser = user;
        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUser_Id(tenantId, linkedUser.getId())
                .orElseGet(() -> TenantMembership.builder()
                        .tenantId(tenantId)
                        .user(linkedUser)
                        .membershipRole(TenantMembershipRole.DRIVER)
                        .status(TenantMembershipStatus.ACTIVE)
                        .grantedPermissions("[]")
                        .revokedPermissions("[]")
                        .permissionsVersion(1L)
                        .lastEventVersion(0L)
                        .build());

        if (membership.getId() != null && membership.getMembershipRole() != TenantMembershipRole.DRIVER) {
            throw new AppException(ErrorCode.TENANT_MEMBERSHIP_ALREADY_EXISTS);
        }
        membership.setMembershipRole(TenantMembershipRole.DRIVER);
        membership.setStatus(TenantMembershipStatus.ACTIVE);
        if (membership.getPermissionsVersion() == null) {
            membership.setPermissionsVersion(1L);
        }
        tenantMembershipRepository.save(membership);

        return linkedUser;
    }

    private void enqueueActivationCodeEmail(
            UUID tenantId,
            UUID userId,
            String recipientEmail,
            String driverName,
            String formattedCode,
            Instant expiresAt) {
        if (jdbcTemplate == null || cipher == null) {
            return;
        }
        try {
            String businessName = tenantDetailsClient.businessName(tenantId);
            Map<String, Object> emailData = new LinkedHashMap<>();
            emailData.put("tenantId", tenantId.toString());
            emailData.put("businessName", businessName != null ? businessName : "Doanh nghiệp SwiftRoute");
            emailData.put("driverName", driverName != null ? driverName : "Tài xế");
            emailData.put("activationCode", formattedCode);
            emailData.put("expiresAtFormatted", VN_EXPIRY_FORMATTER.format(expiresAt));
            String encrypted = cipher.encrypt(objectMapper.writeValueAsString(emailData));
            jdbcTemplate.update(
                    "INSERT INTO auth_notification_outbox(event_id,user_id,email,encrypted_body,event_type) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    userId,
                    recipientEmail,
                    encrypted,
                    ACTIVATION_EMAIL_EVENT_TYPE);
        } catch (Exception ex) {
            log.warn("Failed to enqueue driver activation email for userId={}: {}", userId, ex.getMessage());
        }
    }

    private void enqueueSessionRevokedOutbox(UUID tenantId, UUID userId, UUID sessionId, Instant expiresAt) {
        if (jdbcTemplate == null || cipher == null) {
            return;
        }
        try {
            Map<String, Object> syncPayload = new LinkedHashMap<>();
            syncPayload.put("tenantId", tenantId.toString());
            syncPayload.put("userId", userId.toString());
            syncPayload.put("revokedSessionId", sessionId.toString());
            syncPayload.put("revokedSessionExpiresAtEpochSec", expiresAt.getEpochSecond());
            String encrypted = cipher.encrypt(objectMapper.writeValueAsString(syncPayload));
            jdbcTemplate.update(
                    "INSERT INTO auth_notification_outbox(event_id,user_id,email,encrypted_body,event_type) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    userId,
                    "",
                    encrypted,
                    "tenant.member_permissions_updated.v1");
        } catch (Exception ex) {
            log.warn("Failed to enqueue session revocation sync event for sessionId={}: {}", sessionId, ex.getMessage());
        }
    }

    private void enforceActivationRateLimit(String clientIp, String deviceId) {
        long nowMs = System.currentTimeMillis();
        if (ipRateBuckets.size() > 5000) {
            ipRateBuckets.entrySet().removeIf(entry -> nowMs - entry.getValue().windowStartMs() > RATE_WINDOW_MS * 2);
        }
        if (deviceRateBuckets.size() > 5000) {
            deviceRateBuckets.entrySet().removeIf(entry -> nowMs - entry.getValue().windowStartMs() > RATE_WINDOW_MS * 2);
        }
        if (clientIp != null && !clientIp.isBlank()
                && incrementBucket(ipRateBuckets, clientIp, nowMs) > MAX_ATTEMPTS_PER_IP_PER_MINUTE) {
            throw new AppException(ErrorCode.ACTIVATION_RATE_LIMITED);
        }
        if (deviceId != null && !deviceId.isBlank()
                && incrementBucket(deviceRateBuckets, deviceId, nowMs) > MAX_ATTEMPTS_PER_DEVICE_PER_MINUTE) {
            throw new AppException(ErrorCode.ACTIVATION_RATE_LIMITED);
        }
    }

    private int incrementBucket(Map<String, RateBucket> buckets, String key, long nowMs) {
        RateBucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || nowMs - existing.windowStartMs() >= RATE_WINDOW_MS) {
                return new RateBucket(nowMs, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return bucket.count().get();
    }

    private String generateUniqueTwelveDigitKey() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++) {
                sb.append(secureRandom.nextInt(10));
            }
            String candidate = sb.toString();
            if (!activationKeyRepository.existsByKeyHash(hmacSha256Hex(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique 12-digit activation code");
    }

    public static String formatTwelveDigitKey(String rawTwelveDigits) {
        return rawTwelveDigits.substring(0, 4)
                + " - " + rawTwelveDigits.substring(4, 8)
                + " - " + rawTwelveDigits.substring(8, 12);
    }

    public static String normalizeTwelveDigitKey(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        String stripped = rawInput.replaceAll("[\\s-]+", "");
        return stripped.matches("^\\d{12}$") ? stripped : null;
    }

    private String hmacSha256Hex(String normalizedTwelveDigits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] secretBytes = ("driver-activation-v1:" + hmacSecret).getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedTwelveDigits.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 not available", ex);
        }
    }

    private static String normalizeExternalEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (isSyntheticEmail(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static boolean isSyntheticEmail(String email) {
        return email != null && email.toLowerCase(Locale.ROOT).endsWith(SYNTHETIC_EMAIL_DOMAIN);
    }

    private static String sanitizeOptional(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
