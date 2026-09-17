package servidor.ISO8583;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import servidor.ISO8583.controller.SimulatorController;
import servidor.ISO8583.model.IdempotencySimulationResult;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class IdempotencyFlowTest {

    @Autowired
    private SimulatorController controller;

    @Test
    public void testCompraConIdempotenciaYValidacionesEnSqlServer() {

        controller.reiniciarRateLimit();
        String panValido = "4000000000000001";
        long monto = 50000L; // $500.00

        IdempotencySimulationResult resValido = controller.ejecutarCompraConIdempotencia(panValido, monto);

        System.out.println("\n[TEST RESULT] Compra Exitosa:");
        System.out.println("  1er Envío RC: " + resValido.getPrimerEnvioRc());
        System.out.println("  2do Envío RC: " + resValido.getSegundoEnvioRc());
        System.out.println("  Saldo Inicial: " + resValido.getSaldoInicial());
        System.out.println("  Saldo Final: " + resValido.getSaldoFinal());
        System.out.println("  Idempotencia Exitosa: " + resValido.isIdempotenciaExitosa());
        System.out.println("  Detalle: " + resValido.getDetalle());

        assertEquals("00", resValido.getPrimerEnvioRc(), "El primer envío debe ser aprobado");
        assertEquals("00", resValido.getSegundoEnvioRc(), "El segundo envío duplicado debe responder 00 por Idempotencia");
        assertTrue(resValido.isIdempotenciaExitosa(), "La idempotencia debe cumplirse sin doble cargo");


        controller.reiniciarRateLimit();
        String panInexistente = "9999999999999999";
        IdempotencySimulationResult resInexistente = controller.ejecutarCompraConIdempotencia(panInexistente, monto);
        assertEquals("14", resInexistente.getPrimerEnvioRc(), "Debe retornar 14 si la cuenta no existe en SQL Server");


        controller.reiniciarRateLimit();
        String panSinSaldo = "4000000000000003";
        IdempotencySimulationResult resSinSaldo = controller.ejecutarCompraConIdempotencia(panSinSaldo, monto);
        assertEquals("51", resSinSaldo.getPrimerEnvioRc(), "Debe retornar 51 si no hay saldo suficiente en SQL Server");
    }

    @Test
    public void testCincoClicksConIdempotenciaYRateLimit() {
        controller.reiniciarRateLimit();
        String pan = "4000000000000001";
        long monto = 50000L;
        String stan = controller.generarNuevoStan();
        String rrn = controller.generarNuevoRrn();

        Long saldoAntes = controller.consultarSaldo(pan);


        servidor.ISO8583.model.ClickResult c1 = controller.procesarClickCompra(pan, monto, stan, rrn, 1);
        assertEquals("00", c1.getResponseCode());
        assertTrue(c1.isCobroAplicado());


        servidor.ISO8583.model.ClickResult c2 = controller.procesarClickCompra(pan, monto, stan, rrn, 2);
        assertEquals("00", c2.getResponseCode());
        assertTrue(c2.isDuplicadoIdempotente());


        servidor.ISO8583.model.ClickResult c3 = controller.procesarClickCompra(pan, monto, stan, rrn, 3);
        assertEquals("00", c3.getResponseCode());
        assertTrue(c3.isDuplicadoIdempotente());


        servidor.ISO8583.model.ClickResult c4 = controller.procesarClickCompra(pan, monto, stan, rrn, 4);
        assertEquals("00", c4.getResponseCode());
        assertTrue(c4.isDuplicadoIdempotente());


        servidor.ISO8583.model.ClickResult c5 = controller.procesarClickCompra(pan, monto, stan, rrn, 5);
        assertEquals("13", c5.getResponseCode());
        assertTrue(c5.isBloqueadoPorRateLimit());

        controller.reiniciarRateLimit();
        Long saldoDespues = controller.consultarSaldo(pan);
        assertNotNull(saldoAntes);
        assertNotNull(saldoDespues);
        assertEquals(monto, saldoAntes - saldoDespues, "De los 5 clics solo se debe cobrar UNA SOLA VEZ");
    }

    @Test
    public void testCompraConAnulacion0400Y0410() {
        controller.reiniciarRateLimit();
        String pan = "4000000000000001";
        long monto = 50000L;
        String stan = controller.generarNuevoStan();
        String rrn = controller.generarNuevoRrn();

        Long saldoAntes = controller.consultarSaldo(pan);
        assertNotNull(saldoAntes);

        servidor.ISO8583.model.ClickResult resCompra = controller.procesarClickCompra(pan, monto, stan, rrn, 1);
        assertEquals("00", resCompra.getResponseCode());
        assertTrue(resCompra.isCobroAplicado());

        Long saldoDurante = controller.consultarSaldo(pan);
        assertNotNull(saldoDurante);
        assertEquals(monto, saldoAntes - saldoDurante);

        String rcReverso = controller.procesarReverso(pan, monto, stan, rrn);
        assertEquals("00", rcReverso);

        Long saldoDespues = controller.consultarSaldo(pan);
        assertNotNull(saldoDespues);
        assertEquals(saldoAntes, saldoDespues);
    }
}
