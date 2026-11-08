package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;

class YoutubeStreamExtractorPlayabilityTest {
    @Test
    void detectsCountryRestrictionInTopLevelReason() throws Exception {
        final JsonObject response = JsonParser.object().from("{"
                + "\"playabilityStatus\":{"
                + "\"status\":\"UNPLAYABLE\","
                + "\"reason\":\"The uploader has not made this video available "
                + "in your country\"}}"
        );

        assertTrue(YoutubeStreamExtractor.isGeographicallyRestrictedPlayerResponse(response));
    }

    @Test
    void detectsCountryRestrictionInDetailedReason() throws Exception {
        final JsonObject response = JsonParser.object().from("{"
                + "\"playabilityStatus\":{"
                + "\"status\":\"ERROR\","
                + "\"reason\":\"Video unavailable\","
                + "\"errorScreen\":{\"playerErrorMessageRenderer\":{"
                + "\"subreason\":{\"simpleText\":\"Not available in this country\"}}}}}"
        );

        assertTrue(YoutubeStreamExtractor.isGeographicallyRestrictedPlayerResponse(response));
    }

    @Test
    void doesNotMisclassifyGenericUnavailability() throws Exception {
        final JsonObject response = JsonParser.object().from("{"
                + "\"playabilityStatus\":{"
                + "\"status\":\"UNPLAYABLE\","
                + "\"reason\":\"Video unavailable\"}}"
        );

        assertFalse(YoutubeStreamExtractor.isGeographicallyRestrictedPlayerResponse(response));
    }

    @Test
    void extractsAvailableCountryCodesFromMicroformat() throws Exception {
        final JsonObject response = JsonParser.object().from("{"
                + "\"microformat\":{\"playerMicroformatRenderer\":{"
                + "\"availableCountries\":[\"NL\",\"BE\",\"GB\",\"NL\"]}}}"
        );

        assertEquals(List.of("NL", "BE", "GB"),
                YoutubeStreamExtractor.getAvailableCountries(response));
    }
}
