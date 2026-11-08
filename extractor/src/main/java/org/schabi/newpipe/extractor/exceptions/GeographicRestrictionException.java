package org.schabi.newpipe.extractor.exceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeographicRestrictionException extends ContentNotAvailableException {
    private final List<String> availableCountries;

    public GeographicRestrictionException(final String message) {
        this(message, Collections.emptyList());
    }

    public GeographicRestrictionException(final String message,
                                          final List<String> availableCountries) {
        super(message);
        this.availableCountries = Collections.unmodifiableList(
                new ArrayList<>(availableCountries));
    }

    public GeographicRestrictionException(final String message, final Throwable cause) {
        this(message, cause, Collections.emptyList());
    }

    public GeographicRestrictionException(final String message,
                                          final Throwable cause,
                                          final List<String> availableCountries) {
        super(message, cause);
        this.availableCountries = Collections.unmodifiableList(
                new ArrayList<>(availableCountries));
    }

    /**
     * Returns the ISO 3166-1 alpha-2 country codes where the content is available.
     *
     * @return an immutable list, or an empty list when the service did not provide one
     */
    public List<String> getAvailableCountries() {
        return availableCountries;
    }
}
