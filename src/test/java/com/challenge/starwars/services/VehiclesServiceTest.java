package com.challenge.starwars.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.challenge.starwars.dtos.vehicles.VehicleDetailDto;
import com.challenge.starwars.exceptions.ResourceNotFoundException;
import com.challenge.starwars.mappers.FilmsMapper;
import com.challenge.starwars.mappers.VehiclesMapper;
import com.challenge.starwars.support.Fixtures;
import com.challenge.starwars.support.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehiclesServiceTest {

    @Mock
    private SwapiQueryEngine engine;
    @Mock
    private SwapiCatalogLoader loader;

    private VehiclesService service;

    @BeforeEach
    void setUp() {
        service = new VehiclesService(engine, new VehiclesMapper(), new SwapiCatalog(loader), new FilmsMapper());
    }

    @Test
    void detailResolvesTheFilmsOfTheVehicle() {
        when(loader.load(Resources.VEHICLES)).thenReturn(Fixtures.catalog("vehicles-catalog"));
        when(loader.load(Resources.FILMS)).thenReturn(Fixtures.filmsCatalog());

        VehicleDetailDto crawler = service.detail(4);

        assertThat(crawler.name()).isEqualTo("Sand Crawler");
        assertThat(crawler.films()).extracting("id").containsExactly("1", "5");
    }

    @Test
    void unknownVehicleIsNotFound() {
        when(loader.load(Resources.VEHICLES)).thenReturn(Fixtures.catalog("vehicles-catalog"));

        assertThatThrownBy(() -> service.detail(9999)).isInstanceOf(ResourceNotFoundException.class);
    }
}
