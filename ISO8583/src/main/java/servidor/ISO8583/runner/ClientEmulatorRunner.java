package servidor.ISO8583.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import servidor.ISO8583.controller.SimulatorController;
import servidor.ISO8583.model.ValidationResult;

import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

@Component
@Profile("!test")
@Slf4j
public class ClientEmulatorRunner implements CommandLineRunner {

    @Autowired
    private SimulatorController simulatorController;

    @Autowired
    private Environment environment;

    @Override
    public void run(String... args) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return;
        }

        try {
            Thread.sleep(1200);
        } catch (InterruptedException ignored) {}

        boolean servidorActivo = simulatorController.checkServerHealthNow();
        if (!servidorActivo) {
            System.err.println("\nEl servidor ISO 8583 no está respondiendo o el puerto está cerrado.");
            System.err.println("Se detiene la consola.");
            return;
        }

        simulatorController.setOnServerDownCallback(() -> {
            System.err.println("\nEl servidor ISO 8583 se ha detenido. Cerrando la consola.");
            System.exit(1);
        });

        iniciarMenuInteractivo();
    }

    private void iniciarMenuInteractivo() {
        Scanner scanner = new Scanner(System.in);

        boolean continuar = true;
        while (continuar) {
            if (!simulatorController.isServerHealthy()) {
                System.err.println("\nEl servidor no está respondiendo en el puerto. Deteniendo la consola.");
                break;
            }
            System.out.println("\n----------------------------- MENÚ -----------------------------");
            System.out.println(" [1] Realizar compra (0200) ");
            System.out.println(" [2] Consultar Saldo actual de una cuenta");
            System.out.println(" [3] Realizar compra (0400)");
            System.out.println(" [q] Salir");
            System.out.print("Selecciona una opción: ");

            String opcion = "";
            if (scanner.hasNextLine()) {
                opcion = scanner.nextLine().trim();
            } else {
                break;
            }

            if ("q".equalsIgnoreCase(opcion)) {
                System.out.println("\nCerrando aplicación ISO 8583.");
                continuar = false;
                System.exit(0);
                break;
            }

            switch (opcion) {
                case "1" -> ejecutarFlujoCompraUsuario(scanner);
                case "2" -> ejecutarConsultaSaldo(scanner);
                case "3" -> ejecutarFlujoCompraConAnulacion(scanner);
                default  -> System.out.println("Opción no válida. Intenta nuevamente.");
            }
        }
    }

    private void ejecutarFlujoCompraUsuario(Scanner scanner) {
        if (!simulatorController.isServerHealthy()) {
            System.err.println("Operación denegada: El servidor ISO 8583 no está respondiendo.");
            return;
        }

        System.out.print(" Ingrese el número de cuenta/tarjeta [Default: 4000000000000001]: ");
        String pan = scanner.nextLine().trim();
        if (pan.isEmpty()) {
            pan = "4000000000000001";
        }

        System.out.print(" Ingrese el monto a pagar en pesos [Default: $500.00]: ");
        String montoStr = scanner.nextLine().trim();
        long montoCentavos = 50000L;
        if (!montoStr.isEmpty()) {
            try {
                double montoDouble = Double.parseDouble(montoStr.replace(",", "."));
                montoCentavos = Math.round(montoDouble * 100);
            } catch (NumberFormatException e) {
                System.out.println(" Formato de monto inválido. Se usará el valor por defecto: $500.00");
                montoCentavos = 50000L;
            }
        }

        ValidationResult validacion = simulatorController.validarCuentaYSaldo(pan, montoCentavos);
        if (!validacion.valido()) {
            System.out.println("\n" + validacion.mensaje());
            return;
        }

        Long saldoInicial = validacion.saldo();
        String stan = simulatorController.generarNuevoStan();
        String rrn = simulatorController.generarNuevoRrn();

        simulatorController.reiniciarRateLimit();

        System.out.printf("%nIniciando prueba para Tarjeta %s | Monto: $%,.2f | Saldo Inicial: $%,.2f%n",
                pan, (montoCentavos / 100.0), (saldoInicial / 100.0));

        int totalClicks = 5;

        System.out.printf("%nPresione [ENTER] para simular Clic  (o 's' para salir): ");
        for (int i = 1; i <= totalClicks; i++) {
            String input = "";
            if (scanner.hasNextLine()) {
                input = scanner.nextLine().trim();
            } else {
                break;
            }

            if ("s".equalsIgnoreCase(input)) {
                System.out.println("Simulación de clics interrumpida por el usuario.");
                break;
            }

            servidor.ISO8583.model.ClickResult result = simulatorController.procesarClickCompra(pan, montoCentavos, stan, rrn, i);
        }

        Map<String, Object> cuentaBD = simulatorController.obtenerDetalleCuentaBD(pan);
        if (cuentaBD != null) {
            System.out.printf(" El cobro se ha generado");
        }
    }

    private void ejecutarConsultaSaldo(Scanner scanner) {
        System.out.print("\nIngrese el número de cuenta/tarjeta: ");
        String pan = scanner.nextLine().trim();
        if (pan.isEmpty()) {
            pan = "4000000000000001";
        }

        Long saldo = simulatorController.consultarSaldo(pan);
        if (saldo != null) {
            System.out.printf("Saldo actual para %s: $%,.2f (%d centavos)%n",
                    pan, (saldo / 100.0), saldo);
        } else {
            System.out.printf("No se pudo obtener el saldo para la cuenta %s.%n", pan);
        }
    }

    private void ejecutarFlujoCompraConAnulacion(Scanner scanner) {
        if (!simulatorController.isServerHealthy()) {
            System.err.println("Operación denegada: El servidor ISO 8583 no está respondiendo.");
            return;
        }

        System.out.print(" Ingrese el número de cuenta/tarjeta [Default: 4000000000000001]: ");
        String pan = scanner.nextLine().trim();
        if (pan.isEmpty()) {
            pan = "4000000000000001";
        }

        System.out.print(" Ingrese el monto a pagar en pesos [Default: $500.00]: ");
        String montoStr = scanner.nextLine().trim();
        long montoCentavos = 50000L;
        if (!montoStr.isEmpty()) {
            try {
                double montoDouble = Double.parseDouble(montoStr.replace(",", "."));
                montoCentavos = Math.round(montoDouble * 100);
            } catch (NumberFormatException e) {
                System.out.println(" Formato de monto inválido. Se usará el valor por defecto: $500.00");
                montoCentavos = 50000L;
            }
        }

        ValidationResult validacion = simulatorController.validarCuentaYSaldo(pan, montoCentavos);
        if (!validacion.valido()) {
            System.out.println("\n" + validacion.mensaje());
            return;
        }

        Long saldoAntes = validacion.saldo();
        String stan = simulatorController.generarNuevoStan();
        String rrn = simulatorController.generarNuevoRrn();

        simulatorController.reiniciarRateLimit();

        System.out.println("\n----------------- FLUJO DE COMPRA CON ANULACIÓN (0400) -----------------");
        System.out.printf(" Tarjeta (PAN)         : %s%n", pan);
        System.out.printf(" Monto                 : $%,.2f (%d centavos)%n", (montoCentavos / 100.0), montoCentavos);
        System.out.printf(" STAN                  : %s | RRN: %s%n", stan, rrn);
        System.out.printf(" Saldo ANTES           : $%,.2f (%d centavos)%n", (saldoAntes / 100.0), saldoAntes);

        Long saldoDurante = simulatorController.consultarSaldo(pan);
        System.out.printf("  Saldo DURANTE        : $%,.2f (%d centavos)%n",
                (saldoDurante != null ? saldoDurante / 100.0 : 0.0), (saldoDurante != null ? saldoDurante : 0L));
        System.out.printf("  Monto descontado     : $%,.2f%n", (montoCentavos / 100.0));

        System.out.print("Presione [ENTER] para cancelar la transacción (0400): ");
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }

        System.out.println("Enviando anulación (0400) al servidor...");
        String rcReverso = simulatorController.procesarReverso(pan, montoCentavos, stan, rrn);
        System.out.printf("   RC: %s (REVERSO APROBADO)%n", rcReverso);
        System.out.println("  Mensaje 0410 devuelto al servidor informando que la anulación ha salido bien.");

        Long saldoDespues = simulatorController.consultarSaldo(pan);

        System.out.println("\n=========================== RESUMEN DE BALANCE ===========================");
        System.out.printf(" PAN (Tarjeta)                   : %s%n", pan);
        System.out.printf(" Monto de la operación           : $%,.2f%n", (montoCentavos / 100.0));
        System.out.printf(" * Balance ANTES de la compra    : $%,.2f (%d centavos)%n", (saldoAntes / 100.0), saldoAntes);
        System.out.printf(" * Balance DURANTE (con compra)  : $%,.2f (%d centavos)%n", (saldoDurante != null ? saldoDurante / 100.0 : 0.0), (saldoDurante != null ? saldoDurante : 0L));
        System.out.printf(" * Balance DESPUÉS (con anulación): $%,.2f (%d centavos)%n", (saldoDespues != null ? saldoDespues / 100.0 : 0.0), (saldoDespues != null ? saldoDespues : 0L));

        Map<String, Object> cuentaBD = simulatorController.obtenerDetalleCuentaBD(pan);
        if (cuentaBD != null) {
            System.out.printf("   PAN: %s | Saldo: %s centavos ($%.2f Pesos)%n",
                    cuentaBD.get("pan"), cuentaBD.get("balance"),
                    ((Number) cuentaBD.get("balance")).doubleValue() / 100.0);
        }
    }
}
