package servidor.ISO8583.service.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class IdempotencyService {

    static {
        java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv3, RC4, DES, MD5withRSA");
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    }

    private static final String KEY_PREFIX = "idempotency:";
    private static final String SEPARATOR = ":";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Optional<CachedResponse> findDuplicate(String stan, String rrn, String terminalId) {
        String key = buildKey(stan, rrn, terminalId);

        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("sp_ConsultarIdempotencia")
                    .withoutProcedureColumnMetaDataAccess()
                    .declareParameters(
                            new SqlParameter("p_key", Types.VARCHAR),
                            new SqlOutParameter("o_encontrado", Types.BIT),
                            new SqlOutParameter("o_response_code", Types.VARCHAR)
                    );

            Map<String, Object> out = jdbcCall.execute(Map.of("p_key", key));
            Object encObj = out.get("o_encontrado");
            boolean encontrado = false;
            if (encObj instanceof Boolean b) {
                encontrado = b;
            } else if (encObj instanceof Number n) {
                encontrado = n.intValue() == 1;
            }

            String responseCode = (String) out.get("o_response_code");
            if (encontrado && responseCode != null) {
                return Optional.of(new CachedResponse(responseCode));
            }
        } catch (Exception e) {
            log.error("Error consultando idempotencia: {}", e.getMessage());
        }

        return Optional.empty();
    }

    public void saveResult(String stan, String rrn, String terminalId, String responseCode) {
        String key = buildKey(stan, rrn, terminalId);

        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("sp_GuardarIdempotencia")
                    .withoutProcedureColumnMetaDataAccess()
                    .declareParameters(
                            new SqlParameter("p_key", Types.VARCHAR),
                            new SqlParameter("p_stan", Types.VARCHAR),
                            new SqlParameter("p_rrn", Types.VARCHAR),
                            new SqlParameter("p_terminal_id", Types.VARCHAR),
                            new SqlParameter("p_response_code", Types.VARCHAR)
                    );

            jdbcCall.execute(Map.of(
                    "p_key", key,
                    "p_stan", stan != null ? stan : "",
                    "p_rrn", rrn != null ? rrn : "",
                    "p_terminal_id", terminalId != null ? terminalId : "",
                    "p_response_code", responseCode != null ? responseCode : "00"
            ));
        } catch (Exception e) {
            log.error("Error guardando idempotencia: {}", e.getMessage());
        }
    }

    private String buildKey(String stan, String rrn, String terminalId) {
        return KEY_PREFIX + stan + SEPARATOR + rrn + SEPARATOR + terminalId;
    }

    public record CachedResponse(String responseCode) {}
}
