package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.challenge.starwars.clients.SwapiClient;
import com.challenge.starwars.config.SwapiProperties;
import com.challenge.starwars.dtos.swapi.SwapiItem;
import com.challenge.starwars.dtos.swapi.SwapiPage;
import com.challenge.starwars.exceptions.SwapiUnavailableException;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Paginación y política de caché del catálogo (stale-while-revalidate) con un cliente de origen simulado. */
class SwapiCatalogLoaderTest {

    private static final Duration TTL = Duration.ofHours(1);

    private final SwapiClient swapiClient = mock(SwapiClient.class);
    private final AtomicLong nowNanos = new AtomicLong();
    private final List<Runnable> pendingRefreshes = new ArrayList<>();
    private SwapiCatalogLoader loader;

    @BeforeEach
    void createLoader() {
        // Executor que encola y reloj falso: el refresco corre cuando el test lo decide y el tiempo avanza a mano.
        loader = new SwapiCatalogLoader(swapiClient, TTL, pendingRefreshes::add, nowNanos::get);
    }

    private static SwapiItem item(String uid) {
        return new SwapiItem(uid, null, Map.of("name", "n" + uid));
    }

    private void runPendingRefreshes() {
        List<Runnable> toRun = List.copyOf(pendingRefreshes);
        pendingRefreshes.clear();
        toRun.forEach(Runnable::run);
    }

    private void advancePastTtl() {
        nowNanos.addAndGet(TTL.plusMinutes(1).toNanos());
    }

    @Test
    void loadsAWholePaginatedCollectionPageByPageWithTheMaximumLimit() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100)).thenReturn(new SwapiPage(List.of(item("1"), item("2")), 2));
        when(swapiClient.fetchPage(Resources.PEOPLE, 2, 100)).thenReturn(new SwapiPage(List.of(item("3")), 2));

        Map<String, SwapiItem> people = loader.load(Resources.PEOPLE);

        assertThat(people).containsOnlyKeys("1", "2", "3");
        assertThat(people.keySet()).containsExactly("1", "2", "3");
        verify(swapiClient).fetchPage(Resources.PEOPLE, 1, 100);
        verify(swapiClient).fetchPage(Resources.PEOPLE, 2, 100);
        verify(swapiClient, times(2)).fetchPage(any(), anyInt(), anyInt());
    }

    @Test
    void aCollectionThatFitsOnePageCostsASingleUpstreamCall() {
        when(swapiClient.fetchPage(Resources.STARSHIPS, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("starships-catalog").results(), 1));

        Map<String, SwapiItem> ships = loader.load(Resources.STARSHIPS);

        assertThat(ships.keySet()).containsExactly("2", "9", "10", "12");
        verify(swapiClient, times(1)).fetchPage(any(), anyInt(), anyInt());
    }

    @Test
    void filmsComeFromTheirSingleUnpaginatedCall() {
        when(swapiClient.fetchAll(Resources.FILMS)).thenReturn(Fixtures.search("films-all"));

        Map<String, SwapiItem> films = loader.load(Resources.FILMS);

        assertThat(films.keySet()).containsExactly("1", "2", "3", "4", "5", "6");
        verify(swapiClient).fetchAll(Resources.FILMS);
        verify(swapiClient, never()).fetchPage(any(), anyInt(), anyInt());
    }

    @Test
    void theSecondLoadIsServedFromTheCacheWithoutTouchingUpstream() {
        when(swapiClient.fetchPage(Resources.VEHICLES, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("vehicles-catalog").results(), 1));

        Map<String, SwapiItem> first = loader.load(Resources.VEHICLES);
        Map<String, SwapiItem> second = loader.load(Resources.VEHICLES);

        assertThat(second).isSameAs(first);
        verify(swapiClient, times(1)).fetchPage(any(), anyInt(), anyInt());
    }

    @Test
    void eachResourceHasItsOwnCacheEntry() {
        when(swapiClient.fetchPage(Resources.VEHICLES, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("vehicles-catalog").results(), 1));
        when(swapiClient.fetchPage(Resources.STARSHIPS, 1, 100))
                .thenReturn(new SwapiPage(Fixtures.list("starships-catalog").results(), 1));

        assertThat(loader.load(Resources.VEHICLES)).hasSize(3);
        assertThat(loader.load(Resources.STARSHIPS)).hasSize(4);
    }

    @Test
    void upstreamFailuresAreNotCachedAndTheNextCallRetries() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenThrow(new SwapiUnavailableException("boom", null))
                .thenReturn(new SwapiPage(List.of(item("1")), 1));

        assertThatThrownBy(() -> loader.load(Resources.PEOPLE)).isInstanceOf(SwapiUnavailableException.class);
        Map<String, SwapiItem> recovered = loader.load(Resources.PEOPLE);

        assertThat(recovered).containsOnlyKeys("1");
        verify(swapiClient, times(2)).fetchPage(Resources.PEOPLE, 1, 100);
    }

    @Test
    void aFailureOnALaterPageDiscardsTheWholeLoadInsteadOfCachingAPartialCatalog() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100)).thenReturn(new SwapiPage(List.of(item("1")), 2));
        when(swapiClient.fetchPage(Resources.PEOPLE, 2, 100))
                .thenThrow(new SwapiUnavailableException("boom", null))
                .thenReturn(new SwapiPage(List.of(item("2")), 2));

        assertThatThrownBy(() -> loader.load(Resources.PEOPLE)).isInstanceOf(SwapiUnavailableException.class);

        assertThat(loader.load(Resources.PEOPLE)).containsOnlyKeys("1", "2");
    }

    @Test
    void concurrentColdLoadsShareASingleUpstreamFetch() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100)).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return new SwapiPage(List.of(item("1")), 1);
        });
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<Map<String, SwapiItem>>> results = new ArrayList<>();
            for (int caller = 0; caller < 8; caller++) {
                results.add(callers.submit(() -> loader.load(Resources.PEOPLE)));
            }
            Thread.sleep(200);
            release.countDown();
            for (Future<Map<String, SwapiItem>> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).containsOnlyKeys("1");
            }
        } finally {
            release.countDown();
            callers.shutdownNow();
        }

        verify(swapiClient, times(1)).fetchPage(any(), anyInt(), anyInt());
    }

    @Test
    void afterTheTtlTheStaleCatalogIsStillServedWhileItIsRefreshedInTheBackground() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenReturn(new SwapiPage(List.of(item("1")), 1))
                .thenReturn(new SwapiPage(List.of(item("1"), item("2")), 1));
        Map<String, SwapiItem> original = loader.load(Resources.PEOPLE);

        advancePastTtl();
        Map<String, SwapiItem> triggeringCall = loader.load(Resources.PEOPLE);
        runPendingRefreshes();
        Map<String, SwapiItem> refreshed = loader.load(Resources.PEOPLE);

        assertThat(triggeringCall).isSameAs(original);
        assertThat(refreshed).containsOnlyKeys("1", "2");
        verify(swapiClient, times(2)).fetchPage(Resources.PEOPLE, 1, 100);
    }

    @Test
    void theCatalogNeverExpiresEvenLongAfterTheTtl() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenReturn(new SwapiPage(List.of(item("1")), 1))
                .thenThrow(new SwapiUnavailableException("down", null));
        Map<String, SwapiItem> original = loader.load(Resources.PEOPLE);

        nowNanos.addAndGet(Duration.ofDays(30).toNanos());

        assertThat(loader.load(Resources.PEOPLE)).isSameAs(original);
    }

    @Test
    void aFailedRefreshKeepsServingTheStaleCatalogAndDoesNotRetryBeforeAnotherTtl() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenReturn(new SwapiPage(List.of(item("1")), 1))
                .thenThrow(new SwapiUnavailableException("down", null))
                .thenReturn(new SwapiPage(List.of(item("1"), item("2")), 1));
        Map<String, SwapiItem> original = loader.load(Resources.PEOPLE);

        advancePastTtl();
        Map<String, SwapiItem> triggeringCall = loader.load(Resources.PEOPLE);
        runPendingRefreshes();
        Map<String, SwapiItem> afterFailedRefresh = loader.load(Resources.PEOPLE);
        Map<String, SwapiItem> oneMoreAccess = loader.load(Resources.PEOPLE);

        assertThat(triggeringCall).isSameAs(original);
        assertThat(afterFailedRefresh).isSameAs(original);
        assertThat(oneMoreAccess).isSameAs(original);
        verify(swapiClient, times(2)).fetchPage(Resources.PEOPLE, 1, 100);
    }

    @Test
    void afterAnotherTtlTheRefreshIsRetriedAndASuccessfulRetryReplacesTheCatalog() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenReturn(new SwapiPage(List.of(item("1")), 1))
                .thenThrow(new SwapiUnavailableException("down", null))
                .thenReturn(new SwapiPage(List.of(item("1"), item("2")), 1));
        Map<String, SwapiItem> original = loader.load(Resources.PEOPLE);
        advancePastTtl();
        loader.load(Resources.PEOPLE);
        runPendingRefreshes();

        advancePastTtl();
        Map<String, SwapiItem> triggeringCall = loader.load(Resources.PEOPLE);
        runPendingRefreshes();
        Map<String, SwapiItem> afterRecovery = loader.load(Resources.PEOPLE);

        assertThat(triggeringCall).isSameAs(original);
        assertThat(afterRecovery).containsOnlyKeys("1", "2");
        verify(swapiClient, times(3)).fetchPage(Resources.PEOPLE, 1, 100);
    }

    @Test
    void aColdLoadFailurePropagatesTheOriginalExceptionAndIsNotCached() {
        SwapiUnavailableException failure = new SwapiUnavailableException("down", null);
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100)).thenThrow(failure);

        assertThatThrownBy(() -> loader.load(Resources.PEOPLE)).isSameAs(failure);
        assertThatThrownBy(() -> loader.load(Resources.PEOPLE)).isSameAs(failure);

        verify(swapiClient, times(2)).fetchPage(Resources.PEOPLE, 1, 100);
    }

    @Test
    void invalidateAllForcesAColdLoadOnTheNextAccess() {
        when(swapiClient.fetchPage(Resources.PEOPLE, 1, 100))
                .thenReturn(new SwapiPage(List.of(item("1")), 1))
                .thenReturn(new SwapiPage(List.of(item("2")), 1));
        loader.load(Resources.PEOPLE);

        loader.invalidateAll();

        assertThat(loader.load(Resources.PEOPLE)).containsOnlyKeys("2");
    }

    @Test
    void springWiresTheLoaderThroughItsPublicConstructorAndExposesTheCacheControl() {
        SwapiProperties properties = new SwapiProperties("https://swapi.test/api", 10, TTL,
                new SwapiProperties.Retry(0, Duration.ZERO),
                new SwapiProperties.CircuitBreaker(50, 10, 5, Duration.ofSeconds(30), 1));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SwapiClient.class, () -> swapiClient);
            context.registerBean(SwapiProperties.class, () -> properties);
            context.register(SwapiCatalogLoader.class);
            context.refresh();

            assertThat(context.getBean(CatalogCache.class)).isInstanceOf(SwapiCatalogLoader.class);
        }
    }
}
