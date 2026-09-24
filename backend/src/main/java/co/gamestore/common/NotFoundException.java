package co.gamestore.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
