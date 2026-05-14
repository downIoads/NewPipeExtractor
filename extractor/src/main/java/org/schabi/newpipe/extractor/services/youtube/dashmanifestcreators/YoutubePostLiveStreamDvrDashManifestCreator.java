package org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators;

import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.ALR_YES;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.RN_0;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.SEGMENT_TIMELINE;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.SQ_0;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.buildAndCacheResult;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateDocumentAndDoCommonElementsGeneration;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateSegmentTemplateElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.generateSegmentTimelineElement;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.getInitializationResponse;
import static org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeDashManifestCreatorsUtils.setAttribute;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.services.youtube.DeliveryType;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.utils.ManifestCreatorCache;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * Class which generates DASH manifests of YouTube post-live DVR streams (which use the
 * {@link DeliveryType#LIVE LIVE delivery type}).
 */
public final class YoutubePostLiveStreamDvrDashManifestCreator {

    /**
     * Cache of DASH manifests generated for post-live-DVR streams.
     */
    private static final ManifestCreatorCache<String, String> POST_LIVE_DVR_STREAMS_CACHE
            = new ManifestCreatorCache<>();

    private YoutubePostLiveStreamDvrDashManifestCreator() {
    }

    /**
     * Create DASH manifests from a YouTube post-live-DVR stream/ended livestream.
     *
     * <p>
     * Post-live-DVR streams/ended livestreams are one of the YouTube DASH specific streams which
     * works with sequences and without the need to get a manifest (even if one is provided but not
     * used by main clients (and is not complete for big ended livestreams because it doesn't
     * return the full stream)).
     * </p>
     *
     * <p>
     * They can be found only on livestreams which have ended very recently (a few hours, most of
     * the time)
     * </p>
     *
     * This method needs:
     * <ul>
     *     <li>the base URL of the stream (which, if you try to access to it, returns HTTP
     *     status code 404 after redirects, and if the URL is valid);</li>
     *     <li>an {@link ItagItem}, which needs to contain the following information:
     *         <ul>
     *             <li>its type (see {@link ItagItem.ItagType}), to identify if the content is
     *             an audio or a video stream;</li>
     *             <li>its bitrate;</li>
     *             <li>its mime type;</li>
     *             <li>its codec(s);</li>
     *             <li>for an audio stream: its audio channels;</li>
     *             <li>for a video stream: its width and height.</li>
     *         </ul>
     *     </li>
     *     <li>the duration of the video, which will be used if the duration could not be
     *     parsed from the first sequence of the stream.</li>
     * </ul>
     *
     * In order to generate the DASH manifest, this method will:
     * <ul>
     *     <li>request the first sequence of the stream (the base URL on which the first
     *     sequence parameter is appended (see {@link YoutubeDashManifestCreatorsUtils#SQ_0}))
     *     with a {@code POST} or {@code GET} request (depending of the client on which the
     *     streaming URL comes from is a mobile one ({@code POST}) or not ({@code GET}));</li>
     *     <li>follow its redirection(s), if any;</li>
     *     <li>save the last URL, remove the first sequence parameters;</li>
     *     <li>use the information provided in the {@link ItagItem} to generate all elements
     *     of the DASH manifest.</li>
     * </ul>
     *
     * <p>
     * If the duration cannot be extracted, the {@code durationSecondsFallback} value will be used
     * as the stream duration.
     * </p>
     *
     * @param postLiveStreamDvrStreamingUrl the base URL of the post-live-DVR stream/ended
     *                                      livestream, which must not be null
     * @param itagItem                      the {@link ItagItem} corresponding to the stream, which
     *                                      must not be null
     * @param targetDurationSec             the target duration of each sequence, in seconds (this
     *                                      value is returned with the {@code targetDurationSec}
     *                                      field for each stream in YouTube's player response)
     * @param durationSecondsFallback       the duration of the ended livestream, which will be
     *                                      used if the duration could not be extracted from the
     *                                      first sequence
     * @return the manifest generated into a string
     */
    @Nonnull
    public static String fromPostLiveStreamDvrStreamingUrl(
            @Nonnull final String postLiveStreamDvrStreamingUrl,
            @Nonnull final ItagItem itagItem,
            final int targetDurationSec,
            final long durationSecondsFallback) throws CreationException {
        if (POST_LIVE_DVR_STREAMS_CACHE.containsKey(postLiveStreamDvrStreamingUrl)) {
            return Objects.requireNonNull(
                    POST_LIVE_DVR_STREAMS_CACHE.get(postLiveStreamDvrStreamingUrl)).getSecond();
        }

        // System.err.println("PostLiveDashCreator: enter fromPostLiveStreamDvrStreamingUrl"
        //         + " targetDurationSec=" + targetDurationSec
        //         + " durationSecondsFallback=" + durationSecondsFallback
        //         + " baseUrl=" + postLiveStreamDvrStreamingUrl);

        String realPostLiveStreamDvrStreamingUrl = postLiveStreamDvrStreamingUrl;
        final String streamDurationString;
        final String segmentCount;

        try {
            final long t0 = System.currentTimeMillis();
            // Try to avoid redirects when streaming the content by saving the latest URL we get
            // from video servers.
            final Response response = getInitializationResponse(realPostLiveStreamDvrStreamingUrl,
                    itagItem, DeliveryType.LIVE);
            final long elapsed = System.currentTimeMillis() - t0;
            realPostLiveStreamDvrStreamingUrl = response.latestUrl().replace(SQ_0, "")
                    .replace(RN_0, "").replace(ALR_YES, "");

            final int responseCode = response.responseCode();
            // System.err.println("PostLiveDashCreator: init response code=" + responseCode
            //         + " elapsedMs=" + elapsed
            //         + " latestUrl=" + response.latestUrl());
            // 206 Partial Content is expected when we use a Range request to avoid pulling
            // the full segment body (see getInitializationResponse).
            if (responseCode != 200 && responseCode != 206) {
                throw new CreationException(
                        "Could not get the initialization sequence: response code " + responseCode);
            }

            final Map<String, List<String>> responseHeaders = response.responseHeaders();
            final List<String> timeMillisList = responseHeaders.get("X-Head-Time-Millis");
            final List<String> seqNumList = responseHeaders.get("X-Head-Seqnum");
            // System.err.println("PostLiveDashCreator: X-Head-Time-Millis=" + timeMillisList
            //         + " X-Head-Seqnum=" + seqNumList
            //         + " allHeaderKeys=" + responseHeaders.keySet());
            streamDurationString = timeMillisList.get(0);
            segmentCount = seqNumList.get(0);
        } catch (final IndexOutOfBoundsException | NullPointerException e) {
            throw new CreationException(
                    "Could not get the value of the X-Head-Time-Millis or the X-Head-Seqnum header",
                    e);
        }

        if (isNullOrEmpty(segmentCount)) {
            throw new CreationException("Could not get the number of segments");
        }

        long streamDuration;
        try {
            streamDuration = Long.parseLong(streamDurationString);
        } catch (final NumberFormatException e) {
            streamDuration = durationSecondsFallback;
        }

        // YouTube sometimes omits targetDurationSec from formatData for ended livestreams, so
        // ItagItem.getTargetDurationSec() returns -1. Derive an average segment duration from the
        // authoritative X-Head-Time-Millis / X-Head-Seqnum response headers instead of failing.
        int effectiveTargetDurationSec = targetDurationSec;
        if (effectiveTargetDurationSec <= 0) {
            try {
                final long segCountLong = Long.parseLong(segmentCount);
                if (segCountLong > 0 && streamDuration > 0) {
                    effectiveTargetDurationSec = (int) Math.max(1, Math.round(
                            (double) streamDuration / 1000.0 / (double) segCountLong));
                }
            } catch (final NumberFormatException ignored) {
                // fall through to the throw below
            }
            // System.err.println("PostLiveDashCreator: derived targetDurationSec="
            //         + effectiveTargetDurationSec
            //         + " from streamDuration=" + streamDuration
            //         + " segmentCount=" + segmentCount);
            if (effectiveTargetDurationSec <= 0) {
                throw new CreationException("targetDurationSec is <= 0 and could not be derived "
                        + "from response headers (streamDuration=" + streamDuration
                        + ", segmentCount=" + segmentCount + ")");
            }
        }

        final Document doc = generateDocumentAndDoCommonElementsGeneration(itagItem,
                streamDuration);

        generateSegmentTemplateElement(doc, realPostLiveStreamDvrStreamingUrl,
                DeliveryType.LIVE);
        generateSegmentTimelineElement(doc);
        generateSegmentElementForPostLiveDvrStreams(doc, effectiveTargetDurationSec, segmentCount);

        return buildAndCacheResult(postLiveStreamDvrStreamingUrl, doc,
                POST_LIVE_DVR_STREAMS_CACHE);
    }

    /**
     * @return the cache of DASH manifests generated for post-live-DVR streams
     */
    @Nonnull
    public static ManifestCreatorCache<String, String> getCache() {
        return POST_LIVE_DVR_STREAMS_CACHE;
    }

    /**
     * Generate the segment ({@code <S>}) element.
     *
     * <p>
     * We don't know the exact duration of segments for post-live-DVR streams but an
     * average instead (which is the {@code targetDurationSec} value), so we can use the following
     * structure to generate the segment timeline for DASH manifests of ended livestreams:
     * <br>
     * {@code <S d="targetDurationSecValue" r="segmentCount" />}
     * </p>
     *
     * @param doc                   the {@link Document} on which the {@code <S>} element will
     *                              be appended
     * @param targetDurationSeconds the {@code targetDurationSec} value from YouTube player
     *                              response's stream
     * @param segmentCount          the number of segments, extracted by {@link
     *                              #fromPostLiveStreamDvrStreamingUrl(String, ItagItem, int, long)}
     */
    private static void generateSegmentElementForPostLiveDvrStreams(
            @Nonnull final Document doc,
            final int targetDurationSeconds,
            @Nonnull final String segmentCount) throws CreationException {
        try {
            final Element segmentTimelineElement = (Element) doc.getElementsByTagName(
                    SEGMENT_TIMELINE).item(0);
            final Element sElement = doc.createElement("S");

            setAttribute(sElement, doc, "d", String.valueOf(targetDurationSeconds * 1000));
            setAttribute(sElement, doc, "r", segmentCount);

            segmentTimelineElement.appendChild(sElement);
        } catch (final DOMException e) {
            throw CreationException.couldNotAddElement("segment (S)", e);
        }
    }
}
