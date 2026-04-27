package com.ompasscloud.example.service;

import com.ompasscloud.sdk.OmpassClient;
import com.ompasscloud.sdk.enums.Language;
import com.ompasscloud.sdk.enums.LoginClientType;
import com.ompasscloud.sdk.exception.OmpassApiException;
import com.ompasscloud.sdk.model.request.AuthStartRequest;
import com.ompasscloud.sdk.model.request.TokenVerifyRequest;
import com.ompasscloud.sdk.model.response.AuthStartResponse;
import com.ompasscloud.sdk.model.response.GetAuthenticatorsResponse;
import com.ompasscloud.sdk.model.response.TokenVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OmpassAuthService {

    private static final Logger log = LoggerFactory.getLogger(OmpassAuthService.class);

    private final OmpassClient ompassClient;

    public OmpassAuthService(OmpassClient ompassClient) {
        this.ompassClient = ompassClient;
    }

    public AuthStartResponse startAuth(String username) {
        log.info("Starting OMPASS auth for user: {}", username);

        AuthStartRequest request = AuthStartRequest.builder()
                .username(username)
                .langInit(Language.KR)
                .loginClientType(LoginClientType.BROWSER)
                .sessionTimeoutSeconds(300)
                .build();

        return ompassClient.startAuth(request);
    }

    public TokenVerifyResponse verifyToken(String username, String token) {
        log.info("Verifying OMPASS token for user: {}", username);

        TokenVerifyRequest request = TokenVerifyRequest.builder()
                .username(username)
                .token(token)
                .build();

        return ompassClient.verifyToken(request);
    }

    public boolean hasAuthenticators(String username) {
        log.info("Checking OMPASS authenticators for user: {}", username);

        try {
            GetAuthenticatorsResponse response = ompassClient.getAuthenticators(username);
            return response.getAuthenticators() != null && !response.getAuthenticators().isEmpty();
        } catch (OmpassApiException e) {
            log.warn("Failed to get authenticators for user {}: {}", username, e.getMessage());
            return false;
        }
    }

    public GetAuthenticatorsResponse getAuthenticators(String username) {
        log.info("Getting OMPASS authenticators for user: {}", username);
        return ompassClient.getAuthenticators(username);
    }

    public void deleteAuthenticator(String authenticatorId) {
        log.info("Deleting OMPASS authenticator: {}", authenticatorId);
        ompassClient.deleteAuthenticator(authenticatorId);
    }

    public int deleteAllAuthenticators(String username) {
        log.info("Deleting all OMPASS authenticators for user: {}", username);

        GetAuthenticatorsResponse response = ompassClient.getAuthenticators(username);
        if (response.getAuthenticators() == null || response.getAuthenticators().isEmpty()) {
            return 0;
        }

        int deleted = 0;
        for (var authenticator : response.getAuthenticators()) {
            try {
                ompassClient.deleteAuthenticator(authenticator.getId());
                deleted++;
            } catch (OmpassApiException e) {
                log.warn("Failed to delete authenticator {}: {}", authenticator.getId(), e.getMessage());
            }
        }
        return deleted;
    }
}
