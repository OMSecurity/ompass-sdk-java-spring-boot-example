package com.ompasscloud.example.controller;

import com.ompasscloud.example.domain.User;
import com.ompasscloud.example.service.OmpassAuthService;
import com.ompasscloud.example.service.UserService;
import com.ompasscloud.sdk.exception.OmpassApiException;
import com.ompasscloud.sdk.model.response.AuthStartResponse;
import com.ompasscloud.sdk.model.response.TokenVerifyResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final OmpassAuthService ompassAuthService;

    public AuthController(UserService userService, OmpassAuthService ompassAuthService) {
        this.userService = userService;
        this.ompassAuthService = ompassAuthService;
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String name,
                           @RequestParam String password,
                           RedirectAttributes redirectAttributes) {
        try {
            userService.register(username, email, name, password);
            redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해주세요.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/auth/check-user")
    @ResponseBody
    public Map<String, Object> checkUser(@RequestParam String username) {
        boolean hasAuthenticators = ompassAuthService.hasAuthenticators(username);
        if (!hasAuthenticators) {
            return Map.of("ompassRegistered", false);
        }
        // OMPASS 등록 사용자: 패스워드리스 설정 여부 확인
        var userOpt = userService.findByUsername(username);
        boolean passwordless = userOpt.map(u -> u.isPasswordlessEnabled()).orElse(false);
        return Map.of("ompassRegistered", passwordless);
    }

    @PostMapping("/auth/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        var userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return "redirect:/login";
        }

        if (!userService.verifyPassword(username, password)) {
            redirectAttributes.addFlashAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return "redirect:/login";
        }

        User user = userOpt.get();

        if (user.isOmpassRegistered()) {
            try {
                AuthStartResponse response = ompassAuthService.startAuth(username);

                session.setAttribute("authUsername", username);
                session.setAttribute("ompassRegistered", true);
                session.setAttribute("passwordVerified", true);

                model.addAttribute("username", username);
                model.addAttribute("ompassUrl", response.getOmpassUrl());
                model.addAttribute("isRegistered", response.isRegisteredOmpass());

                return "ompass-auth";
            } catch (OmpassApiException e) {
                log.error("OMPASS auth start failed: {}", e.getMessage());
                redirectAttributes.addFlashAttribute("error", "2FA failed: " + e.getErrorMessage());
                return "redirect:/login";
            }
        } else {
            userService.updateLastLogin(username);
            session.setAttribute("user", user);
            return "redirect:/home";
        }
    }

    @PostMapping("/auth/start")
    @ResponseBody
    public Map<String, Object> startAuth(@RequestParam String username, HttpSession session) {
        log.info("[/auth/start] Starting auth for username: {}", username);

        boolean hasAuthenticators = ompassAuthService.hasAuthenticators(username);
        log.info("[/auth/start] hasAuthenticators result: {}", hasAuthenticators);

        if (!hasAuthenticators) {
            log.warn("[/auth/start] No authenticators found for user: {}", username);
            return Map.of("success", false, "error", "인증을 진행할 수 없습니다.");
        }

        try {
            log.info("[/auth/start] Calling ompassAuthService.startAuth()");
            AuthStartResponse response = ompassAuthService.startAuth(username);
            log.info("[/auth/start] startAuth response - ompassUrl: {}", response.getOmpassUrl());

            session.setAttribute("authUsername", username);
            session.setAttribute("ompassRegistered", true);

            return Map.of(
                "success", true,
                "ompassUrl", response.getOmpassUrl()
            );
        } catch (OmpassApiException e) {
            log.error("[/auth/start] OMPASS auth start failed - errorCode: {}, errorMessage: {}, message: {}",
                e.getErrorCode(), e.getErrorMessage(), e.getMessage());
            return Map.of("success", false, "error", "인증을 진행할 수 없습니다.");
        }
    }

    @PostMapping("/auth/register-ompass")
    @ResponseBody
    public Map<String, Object> registerOmpass(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "error", "로그인이 필요합니다.");
        }

        try {
            AuthStartResponse response = ompassAuthService.startAuth(user.getUsername());

            session.setAttribute("authUsername", user.getUsername());
            session.setAttribute("ompassRegistered", false);
            session.setAttribute("registeringOmpass", true);

            return Map.of(
                "success", true,
                "ompassUrl", response.getOmpassUrl()
            );
        } catch (OmpassApiException e) {
            log.error("OMPASS registration start failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getErrorMessage());
        }
    }

    @PostMapping("/auth/toggle-passwordless")
    @ResponseBody
    public Map<String, Object> togglePasswordless(@RequestParam boolean enabled, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "error", "로그인이 필요합니다.");
        }
        if (!user.isOmpassRegistered()) {
            return Map.of("success", false, "error", "OMPASS가 등록되어 있지 않습니다.");
        }
        userService.updatePasswordlessEnabled(user.getUsername(), enabled);
        User updatedUser = userService.findByUsername(user.getUsername()).orElse(user);
        session.setAttribute("user", updatedUser);
        return Map.of("success", true, "passwordlessEnabled", enabled);
    }

    @PostMapping("/auth/delete-ompass")
    @ResponseBody
    public Map<String, Object> deleteOmpass(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return Map.of("success", false, "error", "로그인이 필요합니다.");
        }

        try {
            int deleted = ompassAuthService.deleteAllAuthenticators(user.getUsername());
            if (deleted > 0) {
                userService.updateOmpassRegistered(user.getUsername(), false);
                User updatedUser = userService.findByUsername(user.getUsername()).orElse(user);
                session.setAttribute("user", updatedUser);
            }
            return Map.of("success", true, "deleted", deleted);
        } catch (OmpassApiException e) {
            log.error("OMPASS delete failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getErrorMessage());
        }
    }

    @GetMapping("/auth/callback")
    public String authCallback(@RequestParam String token,
                               HttpSession session,
                               Model model) {
        String username = (String) session.getAttribute("authUsername");
        if (username == null) {
            model.addAttribute("success", false);
            model.addAttribute("error", "Session expired. Please try again.");
            return "auth-callback";
        }

        try {
            TokenVerifyResponse response = ompassAuthService.verifyToken(username, token);

            if (response.isVerified()) {
                var userOpt = userService.findByUsername(username);
                if (userOpt.isPresent()) {
                    userService.updateOmpassRegistered(username, true);
                    userService.updateLastLogin(username);

                    User user = userService.findByUsername(username).get();
                    session.setAttribute("user", user);
                    session.removeAttribute("authUsername");
                    session.removeAttribute("ompassRegistered");

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    model.addAttribute("success", true);
                    return "auth-callback";
                }
            }

            model.addAttribute("success", false);
            model.addAttribute("error", "Token verification failed.");
            return "auth-callback";
        } catch (OmpassApiException e) {
            log.error("OMPASS token verification failed: {}", e.getMessage());
            model.addAttribute("success", false);
            model.addAttribute("error", "Verification failed: " + e.getErrorMessage());
            return "auth-callback";
        }
    }
}
