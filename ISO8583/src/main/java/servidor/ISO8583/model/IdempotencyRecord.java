package servidor.ISO8583.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {
    private String idempotencyKey;
    private String stan;
    private String rrn;
    private String terminalId;
    private String responseCode;
    private LocalDateTime createdAt;
}
