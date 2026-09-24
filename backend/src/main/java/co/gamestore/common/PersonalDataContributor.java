package co.gamestore.common;

import java.util.Map;

/**
 * Implemented by every module that stores something about a person.
 *
 * <p>Habeas data rights apply to all of it, wherever it lives. Collecting it through a port means a
 * new module cannot quietly fall outside the export or the deletion: it implements this or it holds
 * no personal data, and there is no third option.
 */
public interface PersonalDataContributor {

    /** Section name in the export, for example "orders". */
    String section();

    /** Everything this module holds about the address, in a shape a person can read. */
    Map<String, Object> export(String email);

    /**
     * Removes the link between the records and the person, keeping what the law requires to be kept.
     * An invoice cannot be deleted for ten years; the name on it can stop being a name.
     */
    void anonymize(String email, String pseudonym);
}
