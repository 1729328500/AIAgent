package com.example.userservice.service;

import com.example.userservice.dto.UserCreateRequest;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.dto.UserUpdateRequest;
import com.example.userservice.dto.UserStatusUpdateRequest;
import com.example.userservice.entity.User;
import com.example.userservice.entity.Department;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.UserRole;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ResourceNotFoundException;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.repository.DepartmentRepository;
import com.example.userservice.repository.RoleRepository;
import com.example.userservice.repository.UserRoleRepository;
import com.example.userservice.security.AuditLogService;
import com.example.userservice.security.PasswordEncoder;
import com.example.userservice.security.PhoneEmailValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PhoneEmailValidator phoneEmailValidator;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        validateUserCreateRequest(request);

        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            user.setDepartment(department);
        }

        User savedUser = userRepository.save(user);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            assignRolesToUser(savedUser.getId(), request.getRoleIds());
        }

        auditLogService.logUserCreation(savedUser.getId(), savedUser.getUsername());
        return userMapper.toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (StringUtils.hasText(request.getRealName())) {
            user.setRealName(request.getRealName());
        }

        if (StringUtils.hasText(request.getPhone()) && !user.getPhone().equals(request.getPhone())) {
            validatePhoneUniqueness(request.getPhone());
            user.setPhone(request.getPhone());
        }

        if (StringUtils.hasText(request.getEmail()) && !user.getEmail().equals(request.getEmail())) {
            validateEmailUniqueness(request.getEmail());
            user.setEmail(request.getEmail());
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            user.setDepartment(department);
        }

        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        auditLogService.logUserUpdate(userId, updatedUser.getUsername());
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void updateUserStatus(UUID userId, UserStatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        int oldStatus = user.getStatus();
        int newStatus = request.getStatus();

        if (oldStatus == newStatus) {
            return;
        }

        user.setStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        if (newStatus == 2) {
            revokeAllRolesFromUser(userId);
        }

        auditLogService.logUserStatusChange(userId, user.getUsername(), oldStatus, newStatus);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        userRepository.delete(user);
        auditLogService.logUserDeletion(userId, user.getUsername());
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.logPasswordReset(userId, user.getUsername());
    }

    @Transactional
    public void assignRolesToUser(UUID userId, List<Long> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new ResourceNotFoundException("Some roles not found");
        }

        List<UserRole> existingUserRoles = userRoleRepository.findByUserId(userId);
        List<Long> existingRoleIds = existingUserRoles.stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toList());

        for (Role role : roles) {
            if (!existingRoleIds.contains(role.getId())) {
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                userRole.setCreatedAt(LocalDateTime.now());
                userRoleRepository.save(userRole);
            }
        }

        auditLogService.logRoleAssignment(userId, user.getUsername(), roleIds);
    }

    @Transactional
    public void revokeAllRolesFromUser(UUID userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        userRoleRepository.deleteAll(userRoles);

        auditLogService.logRoleRevocation(userId, userRoles.stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toList()));
    }

    private void validateUserCreateRequest(UserCreateRequest request) {
        if (!phoneEmailValidator.isValidPhone(request.getPhone())) {
            throw new BusinessException("Invalid phone number format");
        }
        if (!phoneEmailValidator.isValidEmail(request.getEmail())) {
            throw new BusinessException("Invalid email format");
        }
        validateUsernameUniqueness(request.getUsername());
        validatePhoneUniqueness(request.getPhone());
        validateEmailUniqueness(request.getEmail());
        validatePasswordStrength(request.getPassword());
    }

    private void validateUsernameUniqueness(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username already exists: " + username);
        }
    }

    private void validatePhoneUniqueness(String phone) {
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("Phone number already exists: " + phone);
        }
    }

    private void validateEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email already exists: " + email);
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("Password must contain at least one digit");
        }
    }
}