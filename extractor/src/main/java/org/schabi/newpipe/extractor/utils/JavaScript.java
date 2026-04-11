package org.schabi.newpipe.extractor.utils;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

import javax.annotation.Nullable;

public final class JavaScript {

    @Nullable
    private static ScriptableObject cachedScope;

    private JavaScript() {
    }

    public static void compileOrThrow(final String function) {
        try (Context context = Context.enter()) {
            context.setInterpretedMode(true);

            // If it doesn't compile it throws an exception here
            context.compileString(function, null, 1, null);
        }
    }

    public static String run(final String function,
                             final String functionName,
                             final String... parameters) {
        try (Context context = Context.enter()) {
            context.setInterpretedMode(true);
            final ScriptableObject scope = context.initSafeStandardObjects();

            context.evaluateString(scope, function, functionName, 1, null);
            final Function jsFunction = (Function) scope.get(functionName, scope);
            final Object result = jsFunction.call(context, scope, scope, parameters);
            return result.toString();
        }
    }

    /**
     * Evaluate JavaScript code once and cache the resulting scope, then call a named function.
     *
     * <p>
     * On the first call (or after {@link #clearCachedScope()}), the code is evaluated to
     * create a scope containing all defined functions and variables. Subsequent calls reuse
     * the cached scope and only invoke the function, which is much faster for large scripts.
     * </p>
     *
     * <p>
     * This method is synchronized to ensure thread safety when accessing the shared scope.
     * </p>
     *
     * @param code         the JavaScript code to evaluate (only on first call or after clear)
     * @param functionName the name of the function to call
     * @param parameters   parameters to pass to the function
     * @return the string result of the function call
     */
    public static synchronized String runCached(final String code,
                                                final String functionName,
                                                final String... parameters) {
        try (Context context = Context.enter()) {
            context.setInterpretedMode(true);

            if (cachedScope == null) {
                cachedScope = context.initSafeStandardObjects();
                context.evaluateString(cachedScope, code, "player", 1, null);
            }

            final Function jsFunction = (Function) cachedScope.get(functionName, cachedScope);
            final Object result = jsFunction.call(context, cachedScope, cachedScope, parameters);
            return result.toString();
        }
    }

    /**
     * Clear the cached JavaScript scope.
     *
     * <p>
     * The next call to {@link #runCached(String, String, String...)} will re-evaluate the
     * code to create a new scope.
     * </p>
     */
    public static synchronized void clearCachedScope() {
        cachedScope = null;
    }

}
