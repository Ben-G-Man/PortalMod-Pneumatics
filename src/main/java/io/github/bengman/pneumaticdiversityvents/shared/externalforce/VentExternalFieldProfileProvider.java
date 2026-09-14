/* Lets a vent section choose endpoint force characteristics without coupling the field manager to block classes. */
package io.github.bengman.pneumaticdiversityvents.shared.externalforce;

public interface VentExternalFieldProfileProvider {
    VentExternalFieldProfile getExternalFieldProfile(boolean intake);
}
