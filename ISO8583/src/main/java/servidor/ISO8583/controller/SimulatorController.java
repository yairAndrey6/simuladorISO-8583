package servidor.ISO8583.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import servidor.ISO8583.model.ClickResult;
import servidor.ISO8583.model.IdempotencySimulationResult;
import servidor.ISO8583.model.ValidationResult;
import servidor.ISO8583.service.simulator.ClientSimulatorService;
import servidor.ISO8583.service.bank.CoreBankingService;
import servidor.ISO8583.service.ratelimiter.RateLimiterService;
import servidor.ISO8583.service.watchdog.WatchdogService;

import java.util.Map;

@Component
@Slf4j
public class SimulatorController {

    @Autowired(required = false)
    private ClientSimulatorService clientSimulatorService;

    @Autowired(required = false)
    private WatchdogService watchdogService;

    @Autowired(required = false)
    private RateLimiterService rateLimiterService;

    @Autowired(required = false)
    private CoreBankingService coreBankingService;

    public SimulatorController() {
        this.clientSimulatorService = new ClientSimulatorService();
        this.watchdogService = new WatchdogService();
    }

    @Autowired
    public SimulatorController(ClientSimulatorService clientSimulatorService,
                               WatchdogService watchdogService,
                               RateLimiterService rateLimiterService,
                               CoreBankingService coreBankingService) {
        this.clientSimulatorService = clientSimulatorService != null ?
                clientSimulatorService : new ClientSimulatorService();
        this.watchdogService = watchdogService != null ?
                watchdogService : new WatchdogService();
        this.rateLimiterService = rateLimiterService;
        this.coreBankingService = coreBankingService;
    }

    public boolean checkServerHealthNow() {
        return watchdogService != null && watchdogService.checkHealthNow();
    }


    public boolean isServerHealthy() {
        return watchdogService == null || watchdogService.isServerHealthy();
    }


    public void setOnServerDownCallback(Runnable callback) {
        if (watchdogService != null) {
            watchdogService.setOnServerDownCallback(callback);
        }
    }


    public String generarNuevoStan() {
        return clientSimulatorService != null ? clientSimulatorService.generarNuevoStan() : "000001";
    }


    public String generarNuevoRrn() {
        return clientSimulatorService != null ? clientSimulatorService.generarNuevoRrn() : "000000000001";
    }


    public void reiniciarRateLimit() {
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }
    }


    public ClickResult procesarClickCompra(String pan, long montoCentavos, String stan, String rrn, int clickNumero) {
        return clientSimulatorService.procesarClickCompra(pan, montoCentavos, stan, rrn, clickNumero);
    }


    public IdempotencySimulationResult ejecutarCompraConIdempotencia(String pan, long montoCentavos) {
        return clientSimulatorService.simularCompraIdempotente(pan, montoCentavos);
    }


    public String procesarReverso(String pan, long montoCentavos, String stan, String rrn) {
        return clientSimulatorService != null ?
                clientSimulatorService.procesarReverso(pan, montoCentavos, stan, rrn) : "96";
    }


    public Long consultarSaldo(String pan) {
        return clientSimulatorService.consultarSaldo(pan);
    }

    public ValidationResult validarCuentaYSaldo(String pan, long montoCentavos) {
        return clientSimulatorService != null ?
                clientSimulatorService.validarCuentaYSaldo(pan, montoCentavos) :
                new ValidationResult(false, "96", "Servicio no disponible", null);
    }


    public Map<String, Object> obtenerDetalleCuentaBD(String pan) {
        return coreBankingService != null ? coreBankingService.obtenerDetalleCuenta(pan) : null;
    }





}
