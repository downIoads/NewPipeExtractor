package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for the unified YouTube player solver approach.
 *
 * <p>
 * YouTube's player JavaScript now uses a unified URL manipulation function that handles both
 * signature and n-parameter deobfuscation through a custom URL class. This class identifies
 * that function by looking for the {@code .set("alr", "yes")} marker and builds executable
 * solver code that can be run in a JavaScript engine.
 * </p>
 *
 * <p>
 * The approach mirrors yt-dlp's ejs (Extractor JavaScript Solver) strategy: extract the IIFE
 * body from the player JS, provide mock browser globals, and inject a solver wrapper function
 * that calls the unified function and reads back the transformed n-parameter from the URL
 * object's custom prototype method.
 * </p>
 */
final class YoutubeUnifiedSolverUtils {

    /**
     * The name of the solver function injected into the player code.
     */
    static final String SOLVER_FUNCTION_NAME = "solve_n";

    /**
     * Pattern to find {@code .set("alr","yes")} or {@code .set("alr", "yes")} calls in the
     * player code. This marker identifies functions involved in URL manipulation for streaming.
     */
    private static final Pattern ALR_YES_PATTERN =
            Pattern.compile("\\.set\\(\"alr\"\\s*,\\s*\"yes\"\\)");

    /**
     * Pattern to find function assignment expressions like {@code name=function(} or
     * {@code name = function(}.
     */
    private static final Pattern FUNC_ASSIGN_PATTERN =
            Pattern.compile("([a-zA-Z0-9_$]+)\\s*=\\s*function\\s*\\(");

    private YoutubeUnifiedSolverUtils() {
    }

    /**
     * Build the unified solver code from YouTube's player JavaScript.
     *
     * <p>
     * This method extracts the IIFE body from the player code, prepends mock browser globals,
     * identifies the unified URL manipulation function(s), and appends a solver wrapper that
     * can deobfuscate n-parameters.
     * </p>
     *
     * @param playerCode the complete YouTube base JavaScript player code
     * @return executable JavaScript code containing a {@code solve_n(n)} function
     * @throws ParsingException if the player code structure cannot be parsed or no unified
     *                          function candidates are found
     */
    @Nonnull
    static String buildSolverCode(@Nonnull final String playerCode) throws ParsingException {
        final List<String> candidates = findCandidateFunctionNames(playerCode);
        if (candidates.isEmpty()) {
            throw new ParsingException(
                    "Could not find any unified solver function candidates in player code");
        }

        final String iifeBody = extractIIFEBody(playerCode);

        final StringBuilder sb = new StringBuilder(iifeBody.length() + 4096);

        // Mock browser globals that the player code expects
        sb.append(getMockGlobals());

        // Define g as empty object (the IIFE normally receives _yt_player as g)
        sb.append("var g = {};\n");

        // The IIFE body, now executing at the top level so all definitions become global
        sb.append(iifeBody);
        sb.append('\n');

        // Solver wrapper function
        sb.append(buildSolverFunction(candidates));

        return sb.toString();
    }

    /**
     * Find candidate function names that could be the unified URL manipulation function.
     *
     * <p>
     * The unified function is identified by these characteristics:
     * <ul>
     *   <li>It's assigned to a simple variable (not a method on an object)</li>
     *   <li>Its body contains {@code .set("alr", "yes")}</li>
     *   <li>Its body contains {@code new g.} (creates a URL object instance)</li>
     * </ul>
     * </p>
     *
     * @param playerCode the complete JavaScript base player code
     * @return list of candidate function names
     */
    @Nonnull
    static List<String> findCandidateFunctionNames(@Nonnull final String playerCode) {
        final List<String> candidates = new ArrayList<>();
        final Matcher alrMatcher = ALR_YES_PATTERN.matcher(playerCode);

        while (alrMatcher.find()) {
            final int alrPos = alrMatcher.start();
            // Look backward up to 500 characters for the enclosing function assignment
            final int searchStart = Math.max(0, alrPos - 500);
            final String before = playerCode.substring(searchStart, alrPos);

            final Matcher funcMatcher = FUNC_ASSIGN_PATTERN.matcher(before);
            String lastFuncName = null;
            int lastFuncAbsStart = -1;
            while (funcMatcher.find()) {
                final int absoluteStart = searchStart + funcMatcher.start();
                // Skip methods defined on objects (e.g., g.A5=function)
                // by checking if the character before the name is a dot
                if (absoluteStart > 0 && playerCode.charAt(absoluteStart - 1) == '.') {
                    continue;
                }
                lastFuncName = funcMatcher.group(1);
                lastFuncAbsStart = absoluteStart;
            }

            if (lastFuncName != null && !candidates.contains(lastFuncName)) {
                // Verify the function creates a new URL object (new g.something)
                final String between = playerCode.substring(lastFuncAbsStart, alrPos);
                if (between.contains("new g.")) {
                    candidates.add(lastFuncName);
                }
            }
        }

        return candidates;
    }

    /**
     * Extract the body of the IIFE (Immediately Invoked Function Expression) from the player
     * code.
     *
     * <p>
     * YouTube's player code is wrapped in an IIFE:
     * {@code var _yt_player={};(function(g){...body...})(_yt_player);}
     * This method extracts the body between the function opening and its closing.
     * </p>
     *
     * @param playerCode the complete JavaScript base player code
     * @return the IIFE body content
     * @throws ParsingException if the IIFE structure cannot be found
     */
    @Nonnull
    private static String extractIIFEBody(@Nonnull final String playerCode)
            throws ParsingException {
        // Find the start of the IIFE body: (function(g){
        final int iifeStart = playerCode.indexOf("(function(g){");
        if (iifeStart < 0) {
            throw new ParsingException("Could not find IIFE start pattern in player code");
        }
        final int bodyStart = iifeStart + "(function(g){".length();

        // Find the end of the IIFE: })(_yt_player)
        final int iifeEnd = playerCode.lastIndexOf("})(_yt_player)");
        if (iifeEnd < 0) {
            throw new ParsingException("Could not find IIFE end pattern in player code");
        }

        if (bodyStart >= iifeEnd) {
            throw new ParsingException("Invalid IIFE structure in player code");
        }

        return playerCode.substring(bodyStart, iifeEnd);
    }

    /**
     * Get JavaScript code that provides mock browser globals needed by the player code.
     *
     * <p>
     * The player code expects browser APIs like {@code window}, {@code document},
     * {@code navigator}, {@code XMLHttpRequest}, and {@code location} to be available.
     * These mocks provide minimal implementations to prevent errors during evaluation.
     * </p>
     */
    @Nonnull
    private static String getMockGlobals() {
        return "var XMLHttpRequest = {prototype: {}};\n"
                + "var location = {"
                + "hash: '', "
                + "host: 'www.youtube.com', "
                + "hostname: 'www.youtube.com', "
                + "href: 'https://www.youtube.com/watch?v=yt-dlp', "
                + "origin: 'https://www.youtube.com', "
                + "pathname: '/watch', "
                + "port: '', "
                + "protocol: 'https:', "
                + "search: '?v=yt-dlp'"
                + "};\n"
                + "if (typeof document === 'undefined') var document = {};\n"
                + "if (typeof navigator === 'undefined') var navigator = {};\n"
                + "if (typeof self === 'undefined') var self = this;\n";
    }

    /**
     * Build the JavaScript solver function that tries each candidate function to deobfuscate
     * the n-parameter.
     *
     * <p>
     * The solver follows yt-dlp's approach:
     * <ol>
     *   <li>Call the candidate function with a dummy URL to get a URL object</li>
     *   <li>Set the n-parameter on the URL object</li>
     *   <li>Find and call the custom method on the URL prototype (not constructor/set/get/clone)
     *       which triggers the n-parameter transformation</li>
     *   <li>Read back the transformed n-parameter from the URL object</li>
     * </ol>
     * Multiple candidates are tried in order; the first one that succeeds wins.
     * </p>
     *
     * @param candidates list of candidate function names to try
     * @return JavaScript code defining the solver function
     */
    @Nonnull
    private static String buildSolverFunction(@Nonnull final List<String> candidates) {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("function ").append(SOLVER_FUNCTION_NAME).append("(n) {\n");
        sb.append("  var candidates = [");
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(candidates.get(i));
        }
        sb.append("];\n");
        sb.append("  var errors = [];\n");
        sb.append("  for (var i = 0; i < candidates.length; i++) {\n");
        sb.append("    try {\n");
        // Call the unified function with a dummy URL, empty sig param name, empty sig
        sb.append("      var url = candidates[i]("
                + "'https://www.youtube.com/watch?v=yt-dlp', '', '');\n");
        // Set the n-parameter on the URL object
        sb.append("      url.set('n', n);\n");
        // Find custom methods on the URL prototype (excluding standard methods)
        sb.append("      var proto = Object.getPrototypeOf(url);\n");
        sb.append("      if (proto) {\n");
        sb.append("        var allKeys = Object.keys(proto);\n");
        sb.append("        var propNames = Object.getOwnPropertyNames(proto);\n");
        sb.append("        var seen = {};\n");
        sb.append("        var keys = [];\n");
        sb.append("        for (var j = 0; j < allKeys.length; j++) {\n");
        sb.append("          if (!seen[allKeys[j]]) { "
                + "seen[allKeys[j]] = true; keys.push(allKeys[j]); }\n");
        sb.append("        }\n");
        sb.append("        for (var j = 0; j < propNames.length; j++) {\n");
        sb.append("          if (!seen[propNames[j]]) { "
                + "seen[propNames[j]] = true; keys.push(propNames[j]); }\n");
        sb.append("        }\n");
        // Call the first custom method (triggers n-parameter transformation)
        sb.append("        for (var j = 0; j < keys.length; j++) {\n");
        sb.append("          var k = keys[j];\n");
        sb.append("          if (k !== 'constructor' && k !== 'set' "
                + "&& k !== 'get' && k !== 'clone') {\n");
        sb.append("            url[k]();\n");
        sb.append("            break;\n");
        sb.append("          }\n");
        sb.append("        }\n");
        sb.append("      }\n");
        // Read back the transformed n-parameter
        sb.append("      var result = url.get('n');\n");
        sb.append("      if (result && result !== n) return result;\n");
        sb.append("    } catch(e) {\n");
        sb.append("      errors.push(e instanceof Error ? e.message : String(e));\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("  throw 'No unified solver candidate succeeded: ' + errors.join(', ');\n");
        sb.append("}\n");
        return sb.toString();
    }
}
