package org.schabi.newpipe.extractor.exceptions;

/**
 * Indicates that the requested content is not yet available, typically because it is a livestream
 * scheduled to start in the future (premiere/upcoming).
 */
public class ContentNotYetAvailableException extends ContentNotAvailableException {
    public ContentNotYetAvailableException(final String message) {
        super(message);
    }

    public ContentNotYetAvailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
