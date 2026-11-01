package com.rims.controller;

import com.rims.dto.AuthDto;
import com.rims.dto.UserDto;
import com.rims.entity.User;
import com.rims.exception.ApiException;
import com.rims.repository.UserRepository;
import com.rims.security.CurrentUser;
import com.rims.security.JwtService;
import com.rims.service.Mappers;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService,
                          CurrentUser currentUser) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username().trim(), req.password()));
        User user = users.findByUsername(req.username().trim())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return new AuthDto.LoginResponse(jwtService.generateToken(user.getUsername(), user.getRole().name()),
                Mappers.user(user));
    }

    @GetMapping("/me")
    public UserDto.Response me() {
        User user = currentUser.get();
        if (user == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return Mappers.user(user);
    }
}
