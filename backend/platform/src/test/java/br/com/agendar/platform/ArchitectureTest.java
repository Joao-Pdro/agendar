package br.com.agendar.platform;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Fronteiras entre os módulos de domínio.
 *
 * <p>Regra: um módulo só pode usar o pacote {@code api} de outro módulo. O pacote
 * {@code internal} de um módulo é privado — nem entidade, nem repositório, nem serviço de outro
 * módulo pode ser importado de fora. É isso que permite extrair um módulo para um processo
 * separado no futuro sem reescrever nada.
 *
 * <p>O {@code platform} é a exceção: ele é o bootstrap e precisa registrar o scan das entidades e
 * repositórios de cada módulo (por nome de pacote, em string, não por import).
 */
@AnalyzeClasses(
        packages = "br.com.agendar",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "br.com.agendar.";

    /**
     * Módulos de domínio existentes hoje. Um módulo novo entra nesta lista quando for criado.
     */
    private static final String[] MODULOS = {"identity", "scheduling", "core"};

    /**
     * Pacotes do bootstrap, isentos da regra: o {@code platform} registra o scan de cada módulo e
     * é a única classe legitimamente acoplada a todos eles.
     */
    private static final String[] ISENTOS = {BASE + "platform"};

    @ArchTest
    static final ArchRule nenhumModuloAcessaInternalDeOutro = classes()
            .should(naoImportarInternalDeOutroModulo())
            .because("cada módulo é dono do próprio internal; de fora só existe o pacote api");

    @ArchTest
    static final ArchRule apiNaoDependeDeInternalDoProprioModulo = classes()
            .that().resideInAnyPackage(pacotesDosModulos(".api.."))
            .should(naoDependerDe("internal"))
            .because("o pacote api é o contrato público e não deve expor tipos internos");

    private static String[] pacotesDosModulos(String sufixo) {
        String[] pacotes = new String[MODULOS.length];
        for (int i = 0; i < MODULOS.length; i++) {
            pacotes[i] = BASE + MODULOS[i] + sufixo;
        }
        return pacotes;
    }

    /**
     * Bootstrap: o próprio pacote {@code br.com.agendar.platform}, sem os subpacotes, que são
     * código comum e portanto obedecem a regra.
     */
    private static boolean isento(String pacote) {
        for (String prefixo : ISENTOS) {
            if (pacote.equals(prefixo)) {
                return true;
            }
        }
        return false;
    }

    private static String moduloDe(String pacote) {
        for (String modulo : MODULOS) {
            if (pacote.startsWith(BASE + modulo + ".")) {
                return modulo;
            }
        }
        return null;
    }

    private static ArchCondition<JavaClass> naoImportarInternalDeOutroModulo() {
        return new ArchCondition<>("não acessar o pacote internal de outro módulo") {
            @Override
            public void check(JavaClass origem, ConditionEvents events) {
                if (isento(origem.getPackageName())) {
                    return;
                }
                String moduloOrigem = moduloDe(origem.getPackageName());

                for (var dependencia : origem.getDirectDependenciesFromSelf()) {
                    String pacoteAlvo = dependencia.getTargetClass().getPackageName();
                    String moduloAlvo = moduloDe(pacoteAlvo);
                    if (moduloAlvo == null || isento(pacoteAlvo)) {
                        continue;
                    }
                    boolean alvoInterno = pacoteAlvo.startsWith(BASE + moduloAlvo + ".internal");
                    boolean deOutroModulo = moduloOrigem == null || !moduloAlvo.equals(moduloOrigem);
                    if (alvoInterno && deOutroModulo) {
                        String de = moduloOrigem == null ? "fora de módulo" : moduloOrigem;
                        events.add(SimpleConditionEvent.violated(dependencia,
                                "%s (%s) acessa %s.internal de outro módulo: %s".formatted(
                                        origem.getName(), de, moduloAlvo,
                                        dependencia.getTargetClass().getName())));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> naoDependerDe(String trechoDoPacote) {
        return new ArchCondition<>("não depender de " + trechoDoPacote) {
            @Override
            public void check(JavaClass origem, ConditionEvents events) {
                origem.getDirectDependenciesFromSelf().forEach(dependencia -> {
                    if (dependencia.getTargetClass().getPackageName().contains(trechoDoPacote)) {
                        events.add(SimpleConditionEvent.violated(dependencia,
                                "%s depende de %s".formatted(
                                        origem.getName(), dependencia.getTargetClass().getName())));
                    }
                });
            }
        };
    }
}
