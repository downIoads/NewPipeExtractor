package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

class YoutubeSearchExtractorTest {
    @Test
    void collectShortsFromGridShelf() throws Exception {
        final JsonObject gridShelf = JsonParser.object().from("{"
                + "\"contents\":[{\"shortsLockupViewModel\":{"
                + "\"overlayMetadata\":{"
                + "\"primaryText\":{\"content\":\"Synthetic short\"},"
                + "\"secondaryText\":{\"content\":\"123 views\"}},"
                + "\"onTap\":{\"innertubeCommand\":{"
                + "\"reelWatchEndpoint\":{\"videoId\":\"abcdefghijk\"}}},"
                + "\"thumbnail\":{\"sources\":[{"
                + "\"url\":\"https://example.com/thumbnail.jpg\","
                + "\"width\":405,\"height\":720}]}}}]}");
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(0);

        YoutubeSearchExtractor.collectShortsFromGridShelf(collector, gridShelf);

        assertEquals(1, collector.getItems().size());
        final InfoItem item = collector.getItems().get(0);
        assertTrue(item instanceof StreamInfoItem);
        assertEquals("Synthetic short", item.getName());
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", item.getUrl());
        assertTrue(((StreamInfoItem) item).isShortFormContent());
    }
}
