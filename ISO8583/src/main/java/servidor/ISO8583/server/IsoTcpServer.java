package servidor.ISO8583.server;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOServer;
import org.jpos.iso.channel.ASCIIChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import servidor.ISO8583.server.handler.IsoMessageHandler;


@Component
@Slf4j
public class IsoTcpServer implements SmartLifecycle {

    @Value("${iso8583.server.port:9999}")
    private int port;

    @Autowired
    private IsoMessageHandler messageHandler;

    @Autowired
    private ISOPackager isoPackager;

    private ISOServer isoServer;
    private Thread serverThread;
    private volatile boolean running = false;


    @Override
    public void start() {
        try {

            ASCIIChannel template = new ASCIIChannel(isoPackager);


            isoServer = new ISOServer(port, template, null);
            isoServer.setConfiguration(new org.jpos.core.SimpleConfiguration());

            isoServer.addISORequestListener(messageHandler);


            serverThread = new Thread(isoServer, "iso8583-tcp-server");
            serverThread.setDaemon(true);
            serverThread.start();

            running = true;
            log.info("Servidor TCP ISO 8583 iniciado en el puerto {}", port);

        } catch (Exception e) {
            log.error("No se pudo iniciar el servidor ISO 8583 en el puerto {}", port, e);
            throw new RuntimeException("Fallo al iniciar servidor ISO 8583", e);
        }
    }

    @Override
    public void stop() {
        log.info("Deteniendo servidor TCP ISO 8583...");
        if (isoServer != null) {
            isoServer.shutdown();
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        running = false;
        log.info("Servidor TCP ISO 8583 detenido.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
