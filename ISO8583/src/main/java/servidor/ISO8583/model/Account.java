package servidor.ISO8583.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private String pan;
    // Saldo en centavos (ej: 5000000 = $50,000.00)
    private Long balance;
}
