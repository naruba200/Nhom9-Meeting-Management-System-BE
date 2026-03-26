package com.example.shopapp.service;

import com.example.shopapp.dto.admin.AdminCreateUserRequest;
import com.example.shopapp.dto.admin.AdminUpdateUserRequest;
import com.example.shopapp.dto.admin.AdminUserResponse;
import com.example.shopapp.entity.ActivityActionType;
import com.example.shopapp.entity.ActivityEntityType;
import com.example.shopapp.entity.User;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public AdminUserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));
        return mapToResponse(user);
    }

    public AdminUserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với email: " + email));
        return mapToResponse(user);
    }

    @Transactional
    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        // Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được đăng ký: " + request.getEmail());
        }

        // Validate role
        String role = request.getRole() != null ? request.getRole().toUpperCase() : "USER";
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new BadRequestException("Role không hợp lệ. Chỉ chấp nhận USER hoặc ADMIN");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(true) // Admin tạo thì auto enable
                .role(role)
                .build();

        userRepository.save(user);
        return mapToResponse(user);
    }

    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        // Cập nhật thông tin
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getRole() != null) {
            String role = request.getRole().toUpperCase();
            if (!"USER".equals(role) && !"ADMIN".equals(role)) {
                throw new BadRequestException("Role không hợp lệ. Chỉ chấp nhận USER hoặc ADMIN");
            }
            user.setRole(role);
        }
        user.setEnabled(request.isEnabled());

        userRepository.save(user);
        return mapToResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User actor = (authentication != null && authentication.getPrincipal() instanceof User authenticatedUser)
            ? authenticatedUser
            : null;

        activityLogService.deleteLogsByUserId(id);

        userRepository.deleteById(id);

        if (actor != null && !actor.getId().equals(id)) {
            activityLogService.logActivity(
                    actor,
                    ActivityActionType.DELETE,
                    ActivityEntityType.USER,
                    targetUser.getId(),
                    "Deleted user",
                    null,
                    null,
                    200,
                    "{\"deletedUserEmail\":\"" + targetUser.getEmail() + "\"}");
        }
    }

    private AdminUserResponse mapToResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .googleCalendarLinked(user.isGoogleCalendarLinked())
                .build();
    }
}
