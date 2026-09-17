package servidor.ISO8583.server.handler;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import servidor.ISO8583.service.bank.CoreBankingService;
import servidor.ISO8583.service.idempotency.IdempotencyService;
import servidor.ISO8583.service.idempotency.IdempotencyService.CachedResponse;
import servidor.ISO8583.service.ratelimiter.RateLimiterService;

import java.util.Optional;


@Component
@Slf4j
public class IsoMessageHandler implements ISORequestListener {

    @Autowired
    private CoreBankingService coreBankingService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RateLimiterService rateLimiterService;


    @Override
    public boolean process(ISOSource source, ISOMsg request) {
        try {
            String stan       = request.getString(11);
            String rrn        = request.getString(37);
            String terminalId = request.getString(41) != null ? request.getString(41).trim() : null;
            String procCode   = request.getString(3);
            boolean isPurchase = "0200".equals(request.getMTI()) && (procCode == null || procCode.startsWith("00"));

            if (isPurchase && terminalId != null && !rateLimiterService.isAllowed(terminalId, stan, rrn)) {
                log.warn("Rate limit alcanzado para terminal: {} con STAN: {}", terminalId, stan);
                ISOMsg rateLimitResponse = buildErrorResponse(request, "13");
                source.send(rateLimitResponse);
                return true;
            }


            if ("0200".equals(request.getMTI()) && stan != null && rrn != null && terminalId != null) {
                Optional<CachedResponse> cached =
                        idempotencyService.findDuplicate(stan, rrn, terminalId);

                if (cached.isPresent()) {
                    log.info("Retornando respuesta cacheada para transacción duplicada | STAN: {}", stan);
                    ISOMsg cachedResponse = buildCachedResponse(request, cached.get());
                    source.send(cachedResponse);
                    return true;
                }
            }


            ISOMsg response = processRequest(request);

            if (response != null) {
                if (stan != null && rrn != null && terminalId != null) {
                    idempotencyService.saveResult(
                            stan, rrn, terminalId,
                            response.getString(39)
                    );
                }

                source.send(response);
            }

            return true;

        } catch (ISOException e) {
            log.error("Error ISO procesando mensaje: {}", e.getMessage(), e);
            sendSystemError(source, request);
            return false;
        } catch (Exception e) {
            log.error("Error inesperado procesando mensaje: {}", e.getMessage(), e);
            sendSystemError(source, request);
            return false;
        }
    }

    private void sendSystemError(ISOSource source, ISOMsg request) {
        try {
            if (request != null && source != null) {
                ISOMsg errorResponse = (ISOMsg) request.clone();
                errorResponse.setResponseMTI();
                errorResponse.set(39, "96");
                source.send(errorResponse);
                log.info("Respuesta de Error");
            }
        } catch (Exception ex) {
            log.error("No se pudo enviar la respuesta: {}", ex.getMessage());
        }
    }


    private ISOMsg buildErrorResponse(ISOMsg request, String responseCode) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setResponseMTI();
        response.set(39, responseCode);
        return response;
    }


    private ISOMsg buildCachedResponse(ISOMsg request, CachedResponse cached) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setResponseMTI();
        response.set(39, cached.responseCode());
        return response;
    }


    private ISOMsg processRequest(ISOMsg request) throws ISOException {
        String mti = request.getMTI();
        return switch (mti) {
            case "0200" -> processFinancialRequest(request);
            case "0400" -> processReversalRequest(request);
            case "0800" -> processNetworkManagement(request);
            case "0210" -> {
                log.info("Notificación recibida del cliente: Compra confirmada exitosamente ",
                        request.getString(11), request.getString(37), request.getString(39));
                yield null;
            }
            default -> {
                yield null;
            }
        };
    }

    private ISOMsg processFinancialRequest(ISOMsg request) throws ISOException {
        ISOMsg response = response(request);

        String pan = request.getString(2);
        String processingCode = request.getString(3);
        String amount = request.getString(4);

        String transactionType = processingCode != null && processingCode.length() >= 2
                ? processingCode.substring(0, 2) : "00";

        String responseCode;

        switch (transactionType) {
            case "00":
                responseCode = coreBankingService.authorizeTransaction(pan, amount);
                break;
            case "21":
                responseCode = coreBankingService.deposit(pan, amount);
                break;
            case "31":
                Long balance = coreBankingService.getBalance(pan);
                if (balance == null) {
                    responseCode = "14";
                } else {
                    responseCode = "00";
                    String balanceStr = String.format("%012d", balance);
                    response.set(54, "1002484C" + balanceStr);
                }
                break;
            default:
                log.warn("Processing code no soportado: {}", processingCode);
                responseCode = "12";
        }

        response.set(39, responseCode);

        return response;
    }

    private ISOMsg processReversalRequest(ISOMsg request) throws ISOException {
        ISOMsg response = response(request);

        String pan = request.getString(2);
        String amount = request.getString(4);

        String responseCode = coreBankingService.reverseTransaction(pan, amount);
        response.set(39, responseCode);

        log.info("Reverso procesado | STAN={} | RC={}",
                request.getString(11), responseCode);
        return response;
    }

    private ISOMsg processNetworkManagement(ISOMsg request) throws ISOException {
        ISOMsg response = response(request);
        response.set(39, "00");
        return response;
    }

    private ISOMsg response(ISOMsg request) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setResponseMTI();
        return response;
    }


}
