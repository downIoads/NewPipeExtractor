package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.utils.JavaScript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class YoutubeUnifiedSolverTest {

    private static final String PLAYER_JS_PATH = "/tmp/youtube_player.js";

    @Test
    void testFindCandidateFunctionNames() throws Exception {
        final String playerCode = readPlayerJs();
        if (playerCode == null) {
            return; // Skip if player JS not available
        }

        final List<String> candidates =
                YoutubeUnifiedSolverUtils.findCandidateFunctionNames(playerCode);

        assertFalse(candidates.isEmpty(),
                "Should find at least one unified solver candidate");
        System.out.println("Found candidates: " + candidates);
    }

    @Test
    void testBuildSolverCode() throws Exception {
        final String playerCode = readPlayerJs();
        if (playerCode == null) {
            return;
        }

        final String solverCode = assertDoesNotThrow(
                () -> YoutubeUnifiedSolverUtils.buildSolverCode(playerCode));

        assertNotNull(solverCode);
        assertFalse(solverCode.isEmpty());
        // Verify the solver function is present in the generated code
        assertFalse(solverCode.indexOf("function solve_n(n)") < 0,
                "Solver code should contain the solve_n function");
        System.out.println("Solver code size: " + solverCode.length() + " chars");
    }

    @Test
    void testSolveNParameter() throws Exception {
        final String playerCode = readPlayerJs();
        if (playerCode == null) {
            return;
        }

        final String solverCode = YoutubeUnifiedSolverUtils.buildSolverCode(playerCode);

        // Use a test n-parameter value (typical format: base64-like alphanumeric string)
        final String testN = "abc123XYZ_test";

        System.out.println("Evaluating solver code (" + solverCode.length() + " chars)...");
        final long startTime = System.currentTimeMillis();

        final String result = JavaScript.runCached(
                solverCode,
                YoutubeUnifiedSolverUtils.SOLVER_FUNCTION_NAME,
                testN);

        final long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Solver completed in " + elapsed + "ms");
        System.out.println("Input:  " + testN);
        System.out.println("Output: " + result);

        assertNotNull(result, "Solver should return a non-null result");
        assertNotEquals(testN, result,
                "Solver should transform the n-parameter (output should differ from input)");
        assertFalse(result.isEmpty(), "Solver should return a non-empty result");

        // Clean up cached scope for subsequent tests
        JavaScript.clearCachedScope();
    }

    @Test
    void testSolveNParameterCachedPerformance() throws Exception {
        final String playerCode = readPlayerJs();
        if (playerCode == null) {
            return;
        }

        final String solverCode = YoutubeUnifiedSolverUtils.buildSolverCode(playerCode);

        // First call (includes evaluation)
        final long start1 = System.currentTimeMillis();
        final String result1 = JavaScript.runCached(
                solverCode,
                YoutubeUnifiedSolverUtils.SOLVER_FUNCTION_NAME,
                "test_n_1");
        final long elapsed1 = System.currentTimeMillis() - start1;

        // Second call (should use cached scope)
        final long start2 = System.currentTimeMillis();
        final String result2 = JavaScript.runCached(
                solverCode,
                YoutubeUnifiedSolverUtils.SOLVER_FUNCTION_NAME,
                "test_n_2");
        final long elapsed2 = System.currentTimeMillis() - start2;

        System.out.println("First call:  " + elapsed1 + "ms (includes evaluation)");
        System.out.println("Second call: " + elapsed2 + "ms (cached scope)");
        System.out.println("Result 1: " + result1);
        System.out.println("Result 2: " + result2);

        assertNotNull(result1);
        assertNotNull(result2);

        // Clean up
        JavaScript.clearCachedScope();
    }

    private String readPlayerJs() {
        final Path path = Path.of(PLAYER_JS_PATH);
        if (!Files.exists(path)) {
            System.out.println("Skipping test: " + PLAYER_JS_PATH + " not found");
            return null;
        }
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            System.out.println("Skipping test: could not read " + PLAYER_JS_PATH);
            return null;
        }
    }
}
