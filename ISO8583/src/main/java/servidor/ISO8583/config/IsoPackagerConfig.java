package servidor.ISO8583.config;

import org.jpos.iso.ISOPackager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import servidor.ISO8583.config.custom.CustomIsoPackager;

@Configuration
public class IsoPackagerConfig {

    @Bean
    public ISOPackager isoPackager() {
        return new CustomIsoPackager();
    }
}
