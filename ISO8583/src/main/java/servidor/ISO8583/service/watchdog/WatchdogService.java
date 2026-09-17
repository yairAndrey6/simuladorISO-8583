package servidor.ISO8583.service.watchdog;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.channel.ASCIIChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import servidor.ISO8583.config.custom.CustomIsoPackager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class WatchdogService {

    @Value("${iso8583.server.host:localhost}")
    private String host = "localhost";

    @Value("${iso8583.server.port:9999}")
    private int port = 9999;

    @Autowired(required = false)
    private ISOPackager isoPackager = new CustomIsoPackager();

    @Autowired(required = false)
    private Environment environment;

    private volatile boolean isServerHealthy = true;
    private Runnable onServerDownCallback;

    public WatchdogService() {
        this.isoPackager = new CustomIsoPackager();
    }

    public WatchdogService(String host, int port, ISOPackager packager) {
        this.host = host;
        this.port = port;
        this.isoPackager = packager != null ? packager : new CustomIsoPackager();
    }

    public void setOnServerDownCallback(Runnable onServerDownCallback) {
        this.onServerDownCallback = onServerDownCallback;
    }


    public boolean isPortOpen(String targetHost, int targetPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public ISOMsg sendNetworkEcho() throws Exception {
        ASCIIChannel channel = new ASCIIChannel(host, port, isoPackager);
        try {
            channel.connect();
            ISOMsg request = new ISOMsg();
            request.setPackager(isoPackager);
            request.setMTI("0800");
            request.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
            request.set(11, String.format("%06d", ThreadLocalRandom.current().nextInt(0, 999_999)));

            channel.send(request);
            ISOMsg response = channel.receive();
            return response;
        } finally {
            channel.disconnect();
        }
    }



    public boolean checkHealthNow() {
        if (!isPortOpen(host, port)) {
            log.error("El puerto {}:{} está CERRADO o el servidor ISO 8583 no responde.", host, port);
            isServerHealthy = false;
            return false;
        }

        try {
            ISOMsg response = sendNetworkEcho();
            String rc = (response != null) ? response.getString(39) : null;

            if ("00".equals(rc)) {
                isServerHealthy = true;
                log.info("Servidor ISO 8583 ACTIVO en {}:{}", host, port);
                return true;
            } else {
                log.warn("Servidor respondió  con código no esperado: {}", rc);
                isServerHealthy = false;
                return false;
            }
        } catch (Exception e) {
            isServerHealthy = false;
            log.error("Error enviando Echo  al servidor {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }


    @Scheduled(fixedRate = 9000000, initialDelay = 100000)
    public void monitorServerHealth() {
        boolean ok = checkHealthNow();
        if (ok) {
            log.info("Servidor estable. Flujo de transacciones permitido.");
        } else {
            log.error("El servidor no está corriendo en el puerto {}. Se detiene la consola.", port);
            detenerConsola();
        }
    }


    public void detenerConsola() {
        if (environment != null && Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return;
        }

        System.err.println("\nEl servidor ISO 8583 en el puerto " + port
                + " no responde o está apagado. Deteniendo la consola...");

        if (onServerDownCallback != null) {
            onServerDownCallback.run();
        } else {
            System.exit(1);
        }
    }

    public boolean isServerHealthy() {
        return isServerHealthy;
    }
}
