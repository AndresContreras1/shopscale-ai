package co.gamestore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The architecture rule that cannot be skipped: modules may only reach each other through the types
 * in their top-level package, and no two modules may depend on each other. A violation fails the
 * build here instead of turning into a tangle nobody can split later.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(GameStoreApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    /**
     * Refreshing the diagrams is deliberate, not automatic: the documenter writes the relations in a
     * different order on every run, so regenerating them during an ordinary build produced a diff on
     * every branch and add/add conflicts whenever two of them were merged.
     *
     * <p>Run it after changing the modules:
     * {@code mvn test -Dtest=ModularityTests -Dmodulith.docs=write}
     */
    @Test
    @EnabledIfSystemProperty(named = "modulith.docs", matches = "write")
    void writesModuleDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules"))
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
