package dev.derops.registry.auth;

import dev.derops.registry.config.AppProperties;
import dev.derops.registry.config.SecurityConfig;
import dev.derops.registry.user.AppUser;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    static final String TOKEN_USE_ONEID_STATE = "oneid_state";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    public record IssuedToken(String value, Instant expiresAt) {}

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder stateDecoder;
    private final AppProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.stateDecoder = NimbusJwtDecoder.withSecretKey(SecurityConfig.signingKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.stateDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwt().issuer()),
                new JwtClaimValidator<String>(SecurityConfig.TOKEN_USE_CLAIM, TOKEN_USE_ONEID_STATE::equals)));
    }

    /** Access token. Permissions are included for the UI only; the API re-checks them from the database. */
    public IssuedToken issueAccessToken(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .claim("perms", user.effectivePermissions().stream().map(Enum::name).sorted().toList())
                .claim(SecurityConfig.TOKEN_USE_CLAIM, SecurityConfig.TOKEN_USE_ACCESS)
                .build();
        return new IssuedToken(encode(claims), expiresAt);
    }

    /** Signed, short-lived OAuth2 "state": lets the callback prove the login started here, without server sessions. */
    public String issueOneIdState() {
        Instant now = clock.instant();
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(STATE_TTL))
                .claim("nonce", HexFormat.of().formatHex(nonce))
                .claim(SecurityConfig.TOKEN_USE_CLAIM, TOKEN_USE_ONEID_STATE)
                .build();
        return encode(claims);
    }

    public boolean isValidOneIdState(String state) {
        try {
            stateDecoder.decode(state);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
