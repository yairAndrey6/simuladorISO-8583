package servidor.ISO8583.service.bank;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.Map;

@Service
@Slf4j
public class CoreBankingService {

    static {
        java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv3, RC4, DES, MD5withRSA");
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    }

    private static final String CORE_BANKING_CB = "coreBanking";

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public Long getBalance(String pan) {
        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("sp_ConsultarSaldo")
                    .withoutProcedureColumnMetaDataAccess()
                    .declareParameters(
                            new SqlParameter("p_pan", Types.VARCHAR),
                            new SqlOutParameter("o_codigo_respuesta", Types.VARCHAR),
                            new SqlOutParameter("o_saldo", Types.BIGINT),
                            new SqlOutParameter("o_mensaje", Types.VARCHAR)
                    );

            Map<String, Object> out = jdbcCall.execute(Map.of("p_pan", pan));
            String rc = (String) out.get("o_codigo_respuesta");
            if ("00".equals(rc)) {
                Number saldo = (Number) out.get("o_saldo");
                return saldo != null ? saldo.longValue() : 0L;
            } else {
                log.warn("Cuenta {} no encontrada: {}", pan, out.get("o_mensaje"));
                return null;
            }
        } catch (Exception e) {
            log.error("[Error al ejecutar la operación para PAN {}: {}", pan, e.getMessage());
            return null;
        }
    }


    public String deposit(String pan, String amountStr) {
        log.info("Solicitando depósito PAN: {} Monto: {}", pan, amountStr);
        long amount = Long.parseLong(amountStr);
        try {
            int updated = jdbcTemplate.update("UPDATE dbo.CUENTAS SET balance = balance + ? WHERE pan = ?", amount, pan);
            if (updated > 0) {
                log.info("Depósito aplicado exitosamente a PAN {}", pan);
                return "00";
            } else {
                log.warn("Cuenta {} no encontrada para depósito", pan);
                return "14";
            }
        } catch (Exception e) {
            log.error("Error aplicando depósito: {}", e.getMessage(), e);
            return "96";
        }
    }


    @CircuitBreaker(name = CORE_BANKING_CB, fallbackMethod = "coreBankingFallback")
    public String authorizeTransaction(String pan, String amountStr) {
        if ("000000099999".equals(amountStr)) {
            throw new RuntimeException("Fallo inducido para comprobación de Circuit Breaker");
        }

        long amount = Long.parseLong(amountStr);

        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("sp_ProcesarCompra")
                    .withoutProcedureColumnMetaDataAccess()
                    .declareParameters(
                            new SqlParameter("p_pan", Types.VARCHAR),
                            new SqlParameter("p_monto", Types.BIGINT),
                            new SqlOutParameter("o_codigo_respuesta", Types.VARCHAR),
                            new SqlOutParameter("o_mensaje", Types.VARCHAR),
                            new SqlOutParameter("o_nuevo_saldo", Types.BIGINT)
                    );

            Map<String, Object> out = jdbcCall.execute(Map.of(
                    "p_pan", pan,
                    "p_monto", amount
            ));

            String responseCode = (String) out.get("o_codigo_respuesta");
            return responseCode != null ? responseCode : "96";

        } catch (Exception e) {
            log.error("Error ejecutando la operación: {}", e.getMessage(), e);
            return "96";
        }
    }


    @CircuitBreaker(name = CORE_BANKING_CB, fallbackMethod = "coreBankingFallback")
    public String reverseTransaction(String pan, String amountStr) {
        log.info("Solicitando en curso..." );
        long amount = Long.parseLong(amountStr);
        try {
            int updated = jdbcTemplate.update("UPDATE dbo.CUENTAS SET balance = balance + ? WHERE pan = ?", amount, pan);
            if (updated > 0) {
                log.info("Reverso aplicado exitosamente a PAN {}", pan);
                return "00";
            } else {
                log.warn("Cuenta {} no encontrada para reverso", pan);
                return "14";
            }
        } catch (Exception e) {
            log.error("Error aplicando reverso: {}", e.getMessage(), e);
            return "96";
        }
    }

    public String coreBankingFallback(String pan, String amount, Throwable t) {
        log.warn("Fallback activado debido a: {}", t.getMessage());
        return "91";
    }


    public Map<String, Object> obtenerDetalleCuenta(String pan) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT pan, balance, CAST((balance / 100.0) AS DECIMAL(18,2)) AS saldo_pesos, created_at FROM dbo.CUENTAS WHERE pan = ?",
                    pan
            );
        } catch (Exception e) {
            return null;
        }
    }


}
