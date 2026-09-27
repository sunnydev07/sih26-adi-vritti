package in.adivritti.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AdiVrittiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdiVrittiApplication.class, args);
    }
}
