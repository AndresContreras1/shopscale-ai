package co.gamestore;

import org.junit.jupiter.api.Test;
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

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules"))
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
