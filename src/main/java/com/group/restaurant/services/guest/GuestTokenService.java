package com.group.restaurant.services.guest;
import org.springframework.beans.factory.annotation.Autowired;

import com.group.restaurant.models.TableSession;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class GuestTokenService {
    private final Key key;
    private final Clock clock;
    @Autowired
    public GuestTokenService(@Value("${spring.app.jwtAccessSecret}") String secret) {
        this(secret, Clock.systemUTC());
    }
    GuestTokenService(String secret, Clock clock) {
        this.clock = clock;
        try {
            // Domain-separated signing key: guest tokens cannot authenticate staff.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("restaurant-guest-v1".getBytes(StandardCharsets.UTF_8));
            key = Keys.hmacShaKeyFor(digest.digest(Decoders.BASE64.decode(secret)));
        } catch (Exception ex) { throw new IllegalStateException("Không khởi tạo được khóa phiên khách", ex); }
    }
    public String issue(TableSession session) {
        if (session.getGuestAccessVersion() == null) session.setGuestAccessVersion(UUID.randomUUID().toString());
        long now = clock.millis();
        return Jwts.builder().setAudience("table-guest").setSubject(session.getId().toString())
                .setId(UUID.randomUUID().toString()).claim("version", session.getGuestAccessVersion())
                .setIssuedAt(new Date(now)).setExpiration(new Date(now + Duration.ofHours(24).toMillis()))
                .signWith(key).compact();
    }
    public void verify(String token, TableSession session) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).requireAudience("table-guest")
                    .setClock(() -> new Date(clock.millis())).build().parseClaimsJws(token).getBody();
            if (!session.getId().toString().equals(claims.getSubject()) || session.getGuestAccessVersion() == null
                    || !session.getGuestAccessVersion().equals(claims.get("version", String.class))) deny();
        } catch (JwtException | IllegalArgumentException ex) { deny(); }
    }
    private void deny() {
        throw new ResponseStatusException(FORBIDDEN, "Phiên khách không hợp lệ hoặc đã hết hạn. Vui lòng quét lại QR và nhờ nhân viên hỗ trợ.");
    }
}
