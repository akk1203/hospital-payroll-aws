package com.hospital.payroll.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CognitoAuthService {

    private final CognitoIdentityProviderClient cognito;
    private final String clientId;
    private final String clientSecret;
    private final String userPoolId;
    private final String hostedUiDomain;
    private final String hostedUiClientId;
    private final String googleEnabled;
    private final String uiOrigin;

    public CognitoAuthService(CognitoIdentityProviderClient cognito,
                              @ConfigProperty(name = "payroll.cognito.client-id", defaultValue = "") String clientId,
                              @ConfigProperty(name = "payroll.cognito.client-secret", defaultValue = "") String clientSecret,
                              @ConfigProperty(name = "payroll.cognito.user-pool-id", defaultValue = "") String userPoolId,
                              @ConfigProperty(name = "payroll.cognito.hosted-ui-domain", defaultValue = "") String hostedUiDomain,
                              @ConfigProperty(name = "payroll.cognito.hosted-ui-client-id", defaultValue = "") String hostedUiClientId,
                              @ConfigProperty(name = "payroll.cognito.google-enabled", defaultValue = "false") String googleEnabled,
                              @ConfigProperty(name = "payroll.ui.origin", defaultValue = "") String uiOrigin) {
        this.cognito = cognito;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.userPoolId = userPoolId;
        this.hostedUiDomain = hostedUiDomain;
        this.hostedUiClientId = hostedUiClientId;
        this.googleEnabled = googleEnabled;
        this.uiOrigin = uiOrigin;
    }

    public Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        boolean google = "true".equalsIgnoreCase(googleEnabled)
                && hostedUiDomain != null && !hostedUiDomain.isBlank()
                && hostedUiClientId != null && !hostedUiClientId.isBlank();
        config.put("googleEnabled", google);
        if (google) {
            String redirect = (uiOrigin == null || uiOrigin.isBlank() ? "" : uiOrigin.replaceAll("/$", "")) + "/";
            config.put("googleLoginUrl", hostedUiDomain.replaceAll("/$", "")
                    + "/oauth2/authorize?client_id=" + hostedUiClientId
                    + "&response_type=token&scope=openid+email+profile"
                    + "&redirect_uri=" + java.net.URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
        return config;
    }

    public AuthResult signup(String name, String email, String password) {
        if (blank(email) || blank(password)) {
            throw new IllegalArgumentException("Email and password are required");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        try {
            cognito.signUp(SignUpRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash(email))
                    .username(email.trim().toLowerCase())
                    .password(password)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email.trim().toLowerCase()).build(),
                            AttributeType.builder().name("name").value(name == null ? email : name).build())
                    .build());
            cognito.adminConfirmSignUp(b -> b.userPoolId(userPoolId).username(email.trim().toLowerCase()));
        } catch (UsernameExistsException ex) {
            throw new IllegalArgumentException("An account with this email already exists");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage() == null ? "Could not create account" : ex.getMessage(), ex);
        }
        return login(email, password);
    }

    public AuthResult login(String email, String password) {
        if (blank(email) || blank(password)) {
            throw new IllegalArgumentException("Email and password are required");
        }
        try {
            var response = cognito.initiateAuth(b -> b
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(clientId)
                    .authParameters(Map.of(
                            "USERNAME", email.trim().toLowerCase(),
                            "PASSWORD", password,
                            "SECRET_HASH", secretHash(email.trim().toLowerCase()))));
            String token = response.authenticationResult().idToken();
            JwtUser user = decode(token);
            return new AuthResult(token, user.email(), user.name());
        } catch (NotAuthorizedException | UserNotConfirmedException ex) {
            throw new IllegalArgumentException("Invalid email or password");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage() == null ? "Could not log in" : ex.getMessage(), ex);
        }
    }

    public JwtUser fromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Not signed in");
        }
        return decode(authorization.substring("Bearer ".length()).trim());
    }

    public JwtUser decode(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid token");
            }
            String json = new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
            String email = extract(json, "email");
            String name = Optional.ofNullable(extract(json, "name")).orElse(email);
            return new JwtUser(email, name);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid token");
        }
    }

    private String secretHash(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((username + clientId).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign Cognito request", ex);
        }
    }

    private static String extract(String json, String key) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    private static String pad(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record AuthResult(String token, String email, String name) {}

    public record JwtUser(String email, String name) {}
}
