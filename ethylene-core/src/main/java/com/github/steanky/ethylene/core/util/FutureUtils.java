package com.github.steanky.ethylene.core.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Contains utility methods for working with {@link CompletableFuture} objects.
 */
public final class FutureUtils {
    private FutureUtils() { throw new AssertionError("No."); }

    /**
     * Constructs a new {@link CompletableFuture} instance from the provided {@link Callable}. If the Callable throw an
     * exception, the CompletableFuture will be completed <i>exceptionally</i> through its
     * {@link CompletableFuture#completeExceptionally(Throwable)} method. Otherwise, it will complete normally with the
     * result of invoking {@link Callable#call()} on the provided Callable. The Callable will be executed asynchronously
     * using the provided {@link Executor}.
     * @param callable the callable to invoke
     * @param executor the executor used to run the callable asynchronously
     * @param <T> the object returned by the callable
     * @return a {@link CompletableFuture} from the given Callable
     */
    public static <T> CompletableFuture<T> callableToFuture(@NotNull Callable<? extends T> callable,
                                                            @NotNull Executor executor) {
        Objects.requireNonNull(callable);
        Objects.requireNonNull(executor);

        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });

        return future;
    }

    /**
     * Works functionally the same as {@link FutureUtils#callableToFuture(Callable, Executor)}, but the given
     * {@link Callable} is run synchronously.
     * @param callable the callable to invoke
     * @param <T> the object returned by the callable
     * @return a {@link CompletableFuture} from the given Callable
     */
    public static <T> CompletableFuture<T> callableToCompletedFuture(@NotNull Callable<? extends T> callable) {
        Objects.requireNonNull(callable);

        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            future.complete(callable.call());
        } catch (Exception exception) {
            future.completeExceptionally(exception);
        }

        return future;
    }
}