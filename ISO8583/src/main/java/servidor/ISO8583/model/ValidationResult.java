package servidor.ISO8583.model;

public record ValidationResult(
        boolean valido,
        String codigoRespuesta,
        String mensaje,
        Long saldo
) {}
