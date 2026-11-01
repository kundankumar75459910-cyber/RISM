package com.rims.service;

import com.rims.dto.UserDto;
import com.rims.entity.Role;
import com.rims.entity.User;
import com.rims.exception.ApiException;
import com.rims.repository.UserRepository;
import com.rims.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;

    public UserService(UserRepository users, PasswordEncoder encoder, CurrentUser currentUser) {
        this.users = users;
        this.encoder = encoder;
        this.currentUser = currentUser;
    }

    public List<UserDto.Response> list() {
        return users.findAll(Sort.by("username")).stream().map(Mappers::user).toList();
    }

    @Transactional
    public UserDto.Response create(UserDto.Request r) {
        String username = r.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username '" + username + "' is already taken");
        }
        checkPassword(r.password(), true);
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setRole(r.role());
        u.setActive(r.active() == null || r.active());
        return Mappers.user(users.save(u));
    }

    @Transactional
    public UserDto.Response update(Long id, UserDto.Request r) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        boolean active = r.active() == null || r.active();
        // an admin must not lock themselves out
        if (u.getUsername().equals(currentUser.username()) && (r.role() != Role.ADMIN || !active)) {
            throw ApiException.badRequest("You cannot remove your own admin role or deactivate your own account");
        }
        if (!u.getUsername().equalsIgnoreCase(r.username().trim())) {
            throw ApiException.badRequest("Usernames cannot be changed");
        }
        u.setFullName(r.fullName().trim());
        u.setRole(r.role());
        u.setActive(active);
        if (r.password() != null && !r.password().isBlank()) {
            checkPassword(r.password(), false);
            u.setPassword(encoder.encode(r.password()));
        }
        return Mappers.user(u);
    }

    private void checkPassword(String password, boolean required) {
        if (password == null || password.isBlank()) {
            if (required) throw ApiException.badRequest("Password is required");
            return;
        }
        if (password.length() < 6) {
            throw ApiException.badRequest("Password must be at least 6 characters");
        }
    }
}
