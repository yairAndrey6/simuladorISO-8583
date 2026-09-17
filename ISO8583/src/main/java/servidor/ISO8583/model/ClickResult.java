package servidor.ISO8583.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickResult {
    private int clickNumero;
    private String stan;
    private String rrn;
    private String pan;
    private long montoCentavos;
    private String responseCode;
    private String statusDescription;
    private boolean cobroAplicado;
    private boolean duplicadoIdempotente;
    private boolean bloqueadoPorRateLimit;
    private String mensajeDetalle;
}
