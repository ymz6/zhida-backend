package org.ymz.app.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * 提供 JWT 核心能力
 *
 * @author ymz
 */
@Getter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtHelper {

    @NotBlank
    private final String secret;
    @NotBlank
    private final String issuer;
    private final SecretKey secretKey;

    public JwtHelper(String secret, String issuer) {
        this.secret = secret;
        this.issuer = issuer;
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * 签发 jwt
     *
     * @param claims        自定义声明
     * @param expireSeconds 生效的秒数
     * @return jwt
     */
    public String generate(@NotNull Claims claims, @Positive long expireSeconds) {
        Instant now = Instant.now();

        return Jwts.builder()
                .claims(claims)
                .issuer(this.issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(this.secretKey)
                .compact();
    }

    /**
     * 解析 jwt
     *
     * @param jwt jwt
     * @return jwt 中的声明
     */
    public Claims parse(@NotBlank String jwt) {
        return Jwts.parser()
                .verifyWith(this.secretKey)
                .requireIssuer(this.issuer)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

}
