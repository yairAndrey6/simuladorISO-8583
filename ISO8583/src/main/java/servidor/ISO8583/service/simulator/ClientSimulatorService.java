package servidor.ISO8583.service.simulator;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.channel.ASCIIChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import servidor.ISO8583.config.custom.CustomIsoPackager;
import servidor.ISO8583.model.ClickResult;
import servidor.ISO8583.model.IdempotencySimulationResult;
import servidor.ISO8583.model.ValidationResult;
import servidor.ISO8583.service.watchdog.WatchdogService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class ClientSimulatorService {

    @Value("${iso8583.server.host:localhost}")
    private String host = "localhost";

    @Value("${iso8583.server.port:9999}")
    private int port = 9999;

    @Autowired(required = false)
    private ISOPackager isoPackager = new CustomIsoPackager();

    @Autowired(required = false)
    private WatchdogService watchdogService;

    public ClientSimulatorService() {
        this.isoPackager = new CustomIsoPackager();
    }

    public ClientSimulatorService(String host, int port, ISOPackager isoPackager, WatchdogService watchdogService) {
        this.host = host;
        this.port = port;
        this.isoPackager = isoPackager != null ? isoPackager : new CustomIsoPackager();
        this.watchdogService = watchdogService;
    }


    public ISOMsg sendFinancialTransaction(String pan, long amount, String stan, String rrn) throws ISOException, IOException {
        ASCIIChannel channel = new ASCIIChannel(host, port, isoPackager);

        try {
            channel.connect();
            ISOMsg request = buildFinancialRequest(pan, amount, "MERCHANT000001", "AVENIDA PRINCIPAL 123", stan, rrn);
            channel.send(request);
            ISOMsg response = channel.receive();
            return response;

        } finally {
            channel.disconnect();
        }
    }

    public void sendConfirmation0210(String pan, long amount, String stan, String rrn) {
        ASCIIChannel channel = new ASCIIChannel(host, port, isoPackager);
        try {
            channel.connect();
            ISOMsg msg = new ISOMsg();
            msg.setPackager(isoPackager);
            msg.setMTI("0210");
            msg.set(2, pan);
            msg.set(3, "000000");
            msg.set(4, String.format("%012d", amount));
            msg.set(7, now("MMddHHmmss"));
            msg.set(11, stan);
            msg.set(37, rrn);
            msg.set(39, "00");
            msg.set(41, "TERM0001");

            channel.send(msg);
            log.info("Mensaje 0210 enviado al servidor ");
        } catch (Exception e) {
            log.error("Error enviando mensaje 0210 al servidor: {}", e.getMessage());
        } finally {
            try {
                channel.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public ISOMsg sendReversalTransaction(String pan, long amount, String stan, String rrn) throws ISOException, IOException {
        ASCIIChannel channel = new ASCIIChannel(host, port, isoPackager);
        try {
            channel.connect();
            ISOMsg request = new ISOMsg();
            request.setPackager(isoPackager);
            request.setMTI("0400");
            request.set(2, pan);
            request.set(3, "000000");
            request.set(4, String.format("%012d", amount));
            request.set(7, now("MMddHHmmss"));
            request.set(11, stan);
            request.set(37, rrn);
            request.set(41, "TERM0001");
            request.set(49, "484");

            channel.send(request);
            return channel.receive();
        } finally {
            channel.disconnect();
        }
    }

    public void sendConfirmation0410(String pan, long amount, String stan, String rrn) {
        ASCIIChannel channel = new ASCIIChannel(host, port, isoPackager);
        try {
            channel.connect();
            ISOMsg msg = new ISOMsg();
            msg.setPackager(isoPackager);
            msg.setMTI("0410");
            msg.set(2, pan);
            msg.set(3, "000000");
            msg.set(4, String.format("%012d", amount));
            msg.set(7, now("MMddHHmmss"));
            msg.set(11, stan);
            msg.set(37, rrn);
            msg.set(39, "00");
            msg.set(41, "TERM0001");

            channel.send(msg);
            log.info("Mensaje 0410 enviado al servidor ");
        } catch (Exception e) {
            log.error("Error enviando mensaje 0410 al servidor: {}", e.getMessage());
        } finally {
            try {
                channel.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public String procesarReverso(String pan, long montoCentavos, String stan, String rrn) {
        try {
            ISOMsg resp = sendReversalTransaction(pan, montoCentavos, stan, rrn);
            String rc = resp != null ? resp.getString(39) : "96";
            if ("00".equals(rc)) {
                sendConfirmation0410(pan, montoCentavos, stan, rrn);
            }
            return rc;
        } catch (Exception e) {
            log.error("Error procesando reverso 0400: {}", e.getMessage());
            return "96";
        }
    }


    public Long sendBalanceInquiry(String pan) throws ISOException, IOException {
        ASCIIChannel channel = new ASCIIChannel(host, port, isoPackager);
        try {
            channel.connect();
            ISOMsg msg = new ISOMsg();
            msg.setPackager(isoPackager);
            msg.setMTI("0200");
            msg.set(2, pan);
            msg.set(3, "310000");
            msg.set(4, "000000000000");
            msg.set(7, now("MMddHHmmss"));
            msg.set(11, randomStan());
            msg.set(41, "TERM0001");

            channel.send(msg);
            ISOMsg response = channel.receive();
            String rc = response.getString(39);

            if ("00".equals(rc) && response.hasField(54)) {
                String field54 = response.getString(54);
                if (field54 != null && field54.length() >= 12) {
                    String amountStr = field54.substring(field54.length() - 12);
                    return Long.parseLong(amountStr);
                }
            }
            return null;
        } finally {
            channel.disconnect();
        }
    }


    public Long consultarSaldo(String pan) {
        if (watchdogService != null && !watchdogService.isServerHealthy()) {
            log.warn("Operación de saldo denegada.");
            return null;
        }

        try {
            return sendBalanceInquiry(pan);
        } catch (Exception e) {
            log.error("Error consultando saldo para PAN {}: {}", pan, e.getMessage());
            return null;
        }
    }

    public ValidationResult validarCuentaYSaldo(String pan, long montoCentavos) {
        if (montoCentavos <= 0) {
            return new ValidationResult(false, "12", "Monto inválido. El monto debe ser mayor a 0.", null);
        }
        Long saldo = consultarSaldo(pan);
        if (saldo == null) {
            return new ValidationResult(false, "14", "Cuenta incorrecta: La tarjeta no existe en la base de datos.", null);
        }
        if (saldo < montoCentavos) {
            return new ValidationResult(false, "51", String.format("Saldo insuficiente: Saldo disponible ($%,.2f) es menor al monto ($%,.2f).", (saldo / 100.0), (montoCentavos / 100.0)), saldo);
        }
        return new ValidationResult(true, "00", "Validación exitosa.", saldo);
    }


    public String generarNuevoStan() {
        return randomStan();
    }

    public String generarNuevoRrn() {
        return randomRrn();
    }


    public ClickResult procesarClickCompra(String pan, long montoCentavos, String stan, String rrn, int clickNumero) {
        if (watchdogService != null && !watchdogService.isServerHealthy()) {
            return ClickResult.builder()
                    .clickNumero(clickNumero)
                    .stan(stan)
                    .rrn(rrn)
                    .pan(pan)
                    .montoCentavos(montoCentavos)
                    .responseCode("91")
                    .statusDescription(resolveStatus("91"))
                    .mensajeDetalle("Servidor ISO 8583 fuera de línea (reportado por Watchdog).")
                    .build();
        }

        try {
            ISOMsg resp = sendFinancialTransaction(pan, montoCentavos, stan, rrn);
            String rc = resp != null ? resp.getString(39) : "96";
            String status = resolveStatus(rc);

            boolean cobroAplicado = false;
            boolean duplicado = false;
            boolean rateLimit = "13".equals(rc);
            String detalle;

            if ("00".equals(rc)) {
                if (clickNumero == 1) {
                    cobroAplicado = true;
                    detalle = "Transacción procesada con éxito. Código 0210 enviado al servidor para informar que todo ha salido bien.";
                    sendConfirmation0210(pan, montoCentavos, stan, rrn);
                } else {
                    duplicado = true;
                    detalle = "Operación duplicada detectada por idempotencia.";
                }
            } else if ("13".equals(rc)) {
                detalle = "Se excedió el límite de peticiones permitidas en la ventana de tiempo.";
            } else if ("14".equals(rc)) {
                if (clickNumero > 1) duplicado = true;
                detalle = "CUENTA NO EXISTE";
            } else if ("51".equals(rc)) {
                if (clickNumero > 1) duplicado = true;
                detalle = "SALDO INSUFICIENTE";
            } else {
                detalle = "Transacción procesada" + rc;
            }

            return ClickResult.builder()
                    .clickNumero(clickNumero)
                    .stan(stan)
                    .rrn(rrn)
                    .pan(pan)
                    .montoCentavos(montoCentavos)
                    .responseCode(rc)
                    .statusDescription(status)
                    .cobroAplicado(cobroAplicado)
                    .duplicadoIdempotente(duplicado)
                    .bloqueadoPorRateLimit(rateLimit)
                    .mensajeDetalle(detalle)
                    .build();

        } catch (Exception e) {
            log.error("Error en clic {}: {}", clickNumero, e.getMessage(), e);
            return ClickResult.builder()
                    .clickNumero(clickNumero)
                    .stan(stan)
                    .rrn(rrn)
                    .pan(pan)
                    .montoCentavos(montoCentavos)
                    .responseCode("96")
                    .statusDescription(resolveStatus("96"))
                    .mensajeDetalle("Error de comunicación socket: " + e.getMessage())
                    .build();
        }
    }

    public IdempotencySimulationResult simularCompraIdempotente(String pan, long montoCentavos) {
        if (watchdogService != null && !watchdogService.isServerHealthy()) {
            return IdempotencySimulationResult.builder()
                    .pan(pan)
                    .montoCentavos(montoCentavos)
                    .primerEnvioRc("91")
                    .primerEnvioStatus(resolveStatus("91"))
                    .segundoEnvioRc("91")
                    .segundoEnvioStatus(resolveStatus("91"))
                    .idempotenciaExitosa(false)
                    .detalle("El servidor ISO 8583 no se encuentra disponible")
                    .build();
        }

        String stan = randomStan();
        String rrn = randomRrn();
        Long saldoInicial = consultarSaldo(pan);
        String primerRc = "96";
        String segundoRc = "96";

        try {
            ISOMsg resp1 = sendFinancialTransaction(pan, montoCentavos, stan, rrn);
            primerRc = resp1 != null ? resp1.getString(39) : "96";
            if ("00".equals(primerRc)) {
                sendConfirmation0210(pan, montoCentavos, stan, rrn);
            }

            ISOMsg resp2 = sendFinancialTransaction(pan, montoCentavos, stan, rrn);
            segundoRc = resp2 != null ? resp2.getString(39) : "96";

        } catch (Exception e) {
            log.error("Error durante la simulación de compra ISO 8583: {}", e.getMessage(), e);
        }

        Long saldoFinal = consultarSaldo(pan);

        boolean esIdempotente = false;
        String detalle;

        if ("00".equals(primerRc)) {
            if ("00".equals(segundoRc)) {
                if (saldoInicial != null && saldoFinal != null) {
                    long diferencia = saldoInicial - saldoFinal;
                    if (diferencia == montoCentavos) {
                        esIdempotente = true;
                        detalle = "OPERACIÓN DUPLICADA";
                    } else if (diferencia == (montoCentavos * 2)) {
                        detalle = "FALLO DE IDEMPOTENCIA: El dinero se descontó dos veces de la base de datos.";
                    } else {
                        detalle = "Transacción aprobada. Saldo inicial: " + saldoInicial + ", saldo final: " + saldoFinal;
                    }
                } else {
                    esIdempotente = true;
                    detalle = "Ambos envíos retornaron RC 00 (Respuesta duplicada cacheada por el servidor).";
                }
            } else {
                detalle = "El segundo envío retornó código inesperado: " + segundoRc;
            }
        } else if ("14".equals(primerRc)) {
            esIdempotente = primerRc.equals(segundoRc);
            detalle = "La cuenta/tarjeta no existe.";
        } else if ("51".equals(primerRc)) {
            esIdempotente = primerRc.equals(segundoRc);
            detalle = "Saldo insuficiente en la cuenta";
        } else {
            esIdempotente = primerRc.equals(segundoRc);
            detalle = "Transacción procesada con código: " + primerRc;
        }

        return IdempotencySimulationResult.builder()
                .pan(pan)
                .montoCentavos(montoCentavos)
                .stan(stan)
                .rrn(rrn)
                .saldoInicial(saldoInicial)
                .primerEnvioRc(primerRc)
                .primerEnvioStatus(resolveStatus(primerRc))
                .segundoEnvioRc(segundoRc)
                .segundoEnvioStatus(resolveStatus(segundoRc))
                .saldoFinal(saldoFinal)
                .idempotenciaExitosa(esIdempotente)
                .detalle(detalle)
                .build();
    }

    public String resolveStatus(String rc) {
        if (rc == null) return "UNKNOWN";
        return switch (rc) {
            case "00" -> "APROBADA (APPROVED)";
            case "14" -> "CUENTA NO EXISTE (INVALID_CARD_NUMBER)";
            case "51" -> "SALDO INSUFICIENTE (INSUFFICIENT_FUNDS)";
            case "13" -> "TRANSACCIÓN INVÁLIDA / RATE LIMIT (INVALID_TRANSACTION)";
            case "91" -> "EMISOR NO DISPONIBLE / CIRCUIT BREAKER (ISSUER_UNAVAILABLE)";
            case "96" -> "ERROR DE SISTEMA (SYSTEM_ERROR)";
            default   -> "RECHAZADA (" + rc + ")";
        };
    }

    private ISOMsg buildFinancialRequest(String pan, long amount, String merchantId, String address, String forceStan, String forceRrn) throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(isoPackager);
        msg.setMTI("0200");

        msg.set(2, pan);
        msg.set(3, "000000");
        msg.set(4, String.format("%012d", amount));
        msg.set(7, now("MMddHHmmss"));
        msg.set(11, (forceStan != null && !forceStan.isEmpty()) ? forceStan : randomStan());
        msg.set(37, (forceRrn != null && !forceRrn.isEmpty()) ? forceRrn : randomRrn());
        msg.set(41, "TERM0001");
        msg.set(42, formatString(merchantId, 15));
        msg.set(43, formatString(address, 40));
        msg.set(49, "484");

        return msg;
    }

    private String formatString(String value, int length) {
        if (value == null) value = "";
        if (value.length() > length) {
            return value.substring(0, length);
        }
        return String.format("%-" + length + "s", value);
    }

    private String now(String pattern) {
        return new SimpleDateFormat(pattern).format(new Date());
    }

    public String randomStan() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 999_999));
    }

    public String randomRrn() {
        return String.format("%012d", ThreadLocalRandom.current().nextInt(0, 999_999_999));
    }
}
