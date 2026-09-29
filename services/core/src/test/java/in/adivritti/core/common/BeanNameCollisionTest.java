package in.adivritti.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bean-name collision guard.
 *
 * <p>Spring Boot 3 sets {@code spring.main.allow-bean-definition-overriding=false},
 * so two definitions that resolve to the same name do not warn and let the last
 * one win: the context refresh throws {@code BeanDefinitionOverrideException} and
 * the service dies at startup. That is exactly how this service broke once --
 * {@code @Component GovsimClient} defaulted to the bean name {@code govsimClient},
 * which is also the name of the {@code @Bean} method building the govsim
 * {@code WebClient} -- and 102 passing unit tests did not notice, because none of
 * them boots a Spring context. Only the compose stack caught it, ten minutes
 * into a CI run.
 *
 * <p>This test reads the sources instead of starting Spring, so it costs
 * milliseconds and needs no database. It is deliberately conservative: it only
 * reports names it can see statically, and it skips test sources.
 */
class BeanNameCollisionTest {

    private static final Path MAIN = Path.of("src", "main", "java", "in", "adivritti", "core");

    /** {@code @Bean} / {@code @Bean("name")} followed by a method name. */
    private static final Pattern BEAN_METHOD = Pattern.compile(
        "@Bean(?:\\(\\s*\"([^\"]+)\"\\s*\\))?\\s*(?:public\\s+|protected\\s+|private\\s+)?"
            + "[\\w<>,\\[\\].?\\s]+?\\s+(\\w+)\\s*\\(");

    /** Stereotype annotations whose implicit bean name is the decapitalised class. */
    private static final Pattern STEREOTYPE = Pattern.compile(
        "@(Component|Service|Repository|Controller|RestController)\\b");

    /** {@code @Component("name")} or {@code @Service("name")} etc. */
    private static final Pattern EXPLICIT_NAME = Pattern.compile(
        "@(?:Component|Service|Repository|Controller|RestController)\\(\\s*\"([^\"]+)\"\\s*\\)");

    /**
     * A type declaration at the start of a line.
     *
     * <p>Deliberately line-anchored. An unanchored {@code class\s+(\w+)} happily
     * matches the phrase "class name" inside a javadoc comment, which silently
     * registers the wrong bean name -- and a guard that cannot see the bug it was
     * written for is worse than no guard.
     */
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
        "(?m)^[ \\t]*(?:(?:public|final|abstract|sealed|non-sealed|static|open)\\s+)*"
            + "(?:class|record|interface|enum)\\s+(\\w+)");

    @Test
    @DisplayName("no component name collides with a @Bean method name")
    void noBeanNameCollisions() throws IOException {
        Map<String, List<String>> stereotypeNames = new HashMap<>();
        Map<String, List<String>> factoryNames = new HashMap<>();

        for (Path file : sourceFiles(MAIN)) {
            String source = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            String label = MAIN.relativize(file).toString();

            // Explicit stereotype name, e.g. @Component("govsimAdapter").
            Matcher explicit = EXPLICIT_NAME.matcher(source);
            if (explicit.find()) {
                add(stereotypeNames, explicit.group(1), label);
                continue;
            }
            // Implicit: the decapitalised simple class name, for a class whose
            // declaration is preceded by a stereotype annotation.
            if (STEREOTYPE.matcher(source).find()) {
                Matcher cls = TYPE_DECLARATION.matcher(source);
                if (cls.find()) {
                    add(stereotypeNames, decapitalise(cls.group(1)), label);
                }
            }

            Matcher bean = BEAN_METHOD.matcher(source);
            while (bean.find()) {
                add(factoryNames,
                    bean.group(1) != null ? bean.group(1) : bean.group(2), label);
            }
        }

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : stereotypeNames.entrySet()) {
            if (factoryNames.containsKey(entry.getKey())) {
                collisions.add(entry.getKey() + ": " + entry.getValue()
                    + " vs @Bean in " + factoryNames.get(entry.getKey()));
            }
        }

        assertThat(collisions)
            .as("stereotype and @Bean names must be unique; Spring Boot 3 refuses to start otherwise")
            .isEmpty();
    }

    @Test
    @DisplayName("the scan actually inspected the configuration classes")
    void scanIsNotSilentlyEmpty() throws IOException {
        // A broken source path would make the guard above pass vacuously, which
        // is the worst possible failure mode for a test whose only job is to
        // catch something no other test can see.
        String all = Files.walk(MAIN)
            .filter(p -> p.toString().endsWith(".java"))
            .map(p -> {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            })
            .reduce("", String::concat);
        assertThat(all).contains("@Configuration", "WebClientConfig", "@Bean");
    }

    private static void add(Map<String, List<String>> target, String key, String label) {
        target.computeIfAbsent(key, k -> new ArrayList<>()).add(label);
    }

    /**
     * Removes comments while preserving line structure, so line-anchored patterns
     * still work and text like "class name" in a javadoc cannot be mistaken for a
     * declaration.
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                } else if (c == '\n') {
                    out.append(c);
                }
                continue;
            }
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
            } else if (c == '/' && next == '/') {
                inLine = true;
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '\'') {
                inChar = true;
                out.append(c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String decapitalise(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static List<Path> sourceFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
