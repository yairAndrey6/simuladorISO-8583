package servidor.ISO8583.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencySimulationResult {
    private String pan;
    private long montoCentavos;
    private String stan;
    private String rrn;
    private Long saldoInicial;
    private String primerEnvioRc;
    private String primerEnvioStatus;
    private String segundoEnvioRc;
    private String segundoEnvioStatus;
    private Long saldoFinal;
    private boolean idempotenciaExitosa;
    private String detalle;
}
