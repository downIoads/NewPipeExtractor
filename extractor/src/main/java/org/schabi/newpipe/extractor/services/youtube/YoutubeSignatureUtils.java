package org.schabi.newpipe.extractor.services.youtube;

import static org.schabi.newpipe.extractor.utils.Parser.matchMultiplePatterns;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.utils.JavaScript;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.jsextractor.JavaScriptExtractor;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to get the signature timestamp of YouTube's base JavaScript player and deobfuscate
 * signature of streaming URLs from HTML5 clients.
 */
final class YoutubeSignatureUtils {

    /**
     * The name of the deobfuscation function which needs to be called inside the deobfuscation
     * code.
     */
    static final String DEOBFUSCATION_FUNCTION_NAME = "deobfuscate";

    private static final Pattern[] FUNCTION_REGEXES = {
            // CHECKSTYLE:OFF
            // Matches: variable&&(variable=functionName(decodeURIComponent(variable)))
            Pattern.compile("\\b(?:[a-zA-Z0-9_$]+)&&\\((?:[a-zA-Z0-9_$]+)=([a-zA-Z0-9_$]{2,})\\(decodeURIComponent\\((?:[a-zA-Z0-9_$]+)\\)\\)"),
            // Matches: variable&&(variable=functionName(additionalParam,decodeURIComponent(variable)))
            Pattern.compile("\\b(?:[a-zA-Z0-9_$]+)&&\\((?:[a-zA-Z0-9_$]+)=([a-zA-Z0-9_$]{2,})\\((\\d+,)decodeURIComponent\\((?:[a-zA-Z0-9_$]+)\\)\\)"),
            Pattern.compile("\\bm=([a-zA-Z0-9$]{2,})\\(decodeURIComponent\\(h\\.s\\)\\)"),
            Pattern.compile("\\bc&&\\(c=([a-zA-Z0-9$]{2,})\\(decodeURIComponent\\(c\\)\\)"),
            Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)"),
            Pattern.compile("([\\w$]+)\\s*=\\s*function\\((\\w+)\\)\\{\\s*\\2=\\s*\\2\\.split\\(\"\"\\)\\s*;")
            // CHECKSTYLE:ON
    };

    private static final String STS_REGEX = "signatureTimestamp[=:](\\d+)";

    private static final String DEOBF_FUNC_REGEX_START = "(";
    private static final String DEOBF_FUNC_REGEX_END = "=function\\([a-zA-Z0-9_]+\\)\\{.+?\\})";

    // CHECKSTYLE:OFF
    private static final String SIG_DEOBF_GLOBAL_ARRAY_REGEX = "(var [A-z]=['\"].*['\"].split\\(\"[;{]\"\\))";
    private static final String SIG_DEOBF_HELPER_OBJ_NAME_REGEX = ";([A-Za-z0-9_\\$]{2,})\\[..";
    private static final String SIG_DEOBF_HELPER_OBJ_REGEX_START = "(var ";
    private static final String SIG_DEOBF_HELPER_OBJ_REGEX_END = "=\\{(?>.|\\n)+?\\}\\};)";
    // CHECKSTYLE:ON

    private YoutubeSignatureUtils() {
    }

    /**
     * Get the signature timestamp property of YouTube's base JavaScript file.
     *
     * @param javaScriptPlayerCode the complete JavaScript base player code
     * @return the signature timestamp
     * @throws ParsingException if the signature timestamp couldn't be extracted
     */
    @Nonnull
    static String getSignatureTimestamp(@Nonnull final String javaScriptPlayerCode)
            throws ParsingException {
        try {
            return Parser.matchGroup1(STS_REGEX, javaScriptPlayerCode);
        } catch (final ParsingException e) {
            throw new ParsingException(
                    "Could not extract signature timestamp from JavaScript code", e);
        }
    }

    /**
     * Get the signature deobfuscation code of YouTube's base JavaScript file.
     *
     * @param javaScriptPlayerCode the complete JavaScript base player code
     * @return the signature deobfuscation code
     * @throws ParsingException if the signature deobfuscation code couldn't be extracted
     */
    @Nonnull
    static String getDeobfuscationCode(@Nonnull final String javaScriptPlayerCode)
            throws ParsingException {
        try {
            final Matcher functionMatcher = getDeobfuscationFunctionMatcher(
                    javaScriptPlayerCode);
            final String deobfuscationFunctionName = functionMatcher.group(1);

            // Check if the regex captured additional parameters (e.g. "0," from newer
            // YouTube player JS that passes an extra param before decodeURIComponent)
            final String functionAdditionalParams = functionMatcher.groupCount() >= 2
                    ? functionMatcher.group(2) : null;

            String deobfuscationFunction;
            try {
                deobfuscationFunction = getDeobfuscateFunctionWithLexer(
                        javaScriptPlayerCode, deobfuscationFunctionName);
            } catch (final Exception e) {
                deobfuscationFunction = getDeobfuscateFunctionWithRegex(
                        javaScriptPlayerCode, deobfuscationFunctionName);
            }

            // Assert the extracted deobfuscation function is valid
            JavaScript.compileOrThrow(deobfuscationFunction);

            final String globalVar =
                    Parser.matchGroup1(SIG_DEOBF_GLOBAL_ARRAY_REGEX, javaScriptPlayerCode);

            final String helperObjectName =
                    Parser.matchGroup1(SIG_DEOBF_HELPER_OBJ_NAME_REGEX, deobfuscationFunction);

            final String helperObject = getHelperObject(javaScriptPlayerCode, helperObjectName);

            final String callerFunction = "function " + DEOBFUSCATION_FUNCTION_NAME
                    + "(a){return "
                    + deobfuscationFunctionName
                    + "(" + (functionAdditionalParams != null ? functionAdditionalParams : "")
                    + "a);}";

            return globalVar + ";" + helperObject + deobfuscationFunction + ";" + callerFunction;
        } catch (final Exception e) {
            throw new ParsingException("Could not parse deobfuscation function", e);
        }
    }

    @Nonnull
    private static Matcher getDeobfuscationFunctionMatcher(
            @Nonnull final String javaScriptPlayerCode)
            throws ParsingException {
        try {
            return matchMultiplePatterns(FUNCTION_REGEXES, javaScriptPlayerCode);
        } catch (final Parser.RegexException e) {
            throw new ParsingException(
                    "Could not find deobfuscation function with any of the known patterns", e);
        }
    }

    @Nonnull
    private static String getDeobfuscateFunctionWithLexer(
            @Nonnull final String javaScriptPlayerCode,
            @Nonnull final String deobfuscationFunctionName) throws ParsingException {
        final String functionBase = deobfuscationFunctionName + "=function";
        return functionBase + JavaScriptExtractor.matchToClosingBrace(
                javaScriptPlayerCode, functionBase);
    }

    @Nonnull
    private static String getDeobfuscateFunctionWithRegex(
            @Nonnull final String javaScriptPlayerCode,
            @Nonnull final String deobfuscationFunctionName) throws ParsingException {
        final String functionPattern = DEOBF_FUNC_REGEX_START
                + Pattern.quote(deobfuscationFunctionName)
                + DEOBF_FUNC_REGEX_END;
        return "var " + Parser.matchGroup1(functionPattern, javaScriptPlayerCode);
    }

    @Nonnull
    private static String getHelperObject(@Nonnull final String javaScriptPlayerCode,
                                          @Nonnull final String helperObjectName)
            throws ParsingException {
        final String helperPattern = SIG_DEOBF_HELPER_OBJ_REGEX_START
                + Pattern.quote(helperObjectName)
                + SIG_DEOBF_HELPER_OBJ_REGEX_END;
        return Parser.matchGroup1(helperPattern, javaScriptPlayerCode)
                .replace("\n", "");
    }
}
