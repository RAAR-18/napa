package co.edu.napa.cuentas.infrastructure.adapters.out.dns;

import co.edu.napa.cuentas.application.ports.out.DominioCorreoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta el DNS (MX y, si no hay, A) para saber si un dominio recibe correo.
 * Si el DNS falla por otra razón, no bloquea el registro.
 */
@Component
public class DnsDominioCorreoAdapter implements DominioCorreoPort {
    private static final Logger log = LoggerFactory.getLogger(DnsDominioCorreoAdapter.class);
    private final ConcurrentHashMap<String, Boolean> conocidos = new ConcurrentHashMap<>();

    @Override
    public boolean recibeCorreo(String dominio) {
        Boolean conocido = conocidos.get(dominio);
        if (conocido != null) {
            return conocido;
        }
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");
        try {
            InitialDirContext dns = new InitialDirContext(env);
            try {
                boolean recibe = tieneRegistro(dns.getAttributes(dominio, new String[]{"MX"}).get("MX"))
                        || tieneRegistro(dns.getAttributes(dominio, new String[]{"A"}).get("A"));
                if (conocidos.size() < 10_000) {
                    conocidos.put(dominio, recibe);
                }
                return recibe;
            } finally {
                dns.close();
            }
        } catch (NameNotFoundException exception) {
            if (conocidos.size() < 10_000) {
                conocidos.put(dominio, false);
            }
            return false;
        } catch (NamingException exception) {
            log.warn("No se pudo consultar el DNS de {}: {}", dominio, exception.getMessage());
            return true;
        }
    }

    private static boolean tieneRegistro(Attribute attribute) throws NamingException {
        return attribute != null && attribute.size() > 0 && attribute.get() != null;
    }
}
