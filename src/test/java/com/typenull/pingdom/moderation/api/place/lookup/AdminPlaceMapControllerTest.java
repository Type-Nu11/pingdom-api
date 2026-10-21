package com.typenull.pingdom.moderation.api.place.lookup;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapQueryService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminPlaceMapControllerTest {
    private final AdminPlaceMapQueryService service = mock(AdminPlaceMapQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminPlaceMapController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class))).build();
    }

    private MockHttpServletRequestBuilder request() {
        return get("/admin/places/map").param("west","128.5").param("south","35.8")
                .param("east","128.7").param("north","35.95").param("zoom","13")
                .param("width","1200").param("height","800");
    }

    @Test
    void bindsMapParametersAndReturnsNoStoreEmptyResponse() throws Exception {
        when(service.find(any())).thenReturn(new AdminPlaceMapResponse("SPATIAL",13,List.of(),List.of(),List.of(),0,true,null,null));
        mvc.perform(request().param("discoveryStatus","HIDDEN").param("category","CAFE"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.mode").value("SPATIAL")).andExpect(jsonPath("$.regions").isArray())
                .andExpect(jsonPath("$.clusters").isArray()).andExpect(jsonPath("$.markers").isArray())
                .andExpect(jsonPath("$.viewportPlaceCount").value(0)).andExpect(jsonPath("$.complete").value(true));
        verify(service).find(argThat(r -> r.zoom()==13 && r.width()==1200 && r.category().equals("CAFE")
                && r.discoveryStatus().name().equals("HIDDEN")));
    }

    @Test
    void returnsRepresentativeObjectAlongsideExistingClusterContract() throws Exception {
        var representative = new AdminPlaceMapResponse.Marker(10,"대표 카페",35.87,128.6,"CAFE","VISIBLE","OPERATING");
        var cluster = new AdminPlaceMapResponse.Cluster("key","token",24,representative,35.88,128.61,
                new AdminPlaceMapResponse.Bounds(128.5,35.8,128.7,35.95),"VIEWPORT","EXPAND",14);
        when(service.find(any())).thenReturn(new AdminPlaceMapResponse("SPATIAL",13,List.of(),List.of(cluster),List.of(),24,true,null,null));
        mvc.perform(request()).andExpect(status().isOk())
                .andExpect(jsonPath("$.clusters[0].representative.id").value(10))
                .andExpect(jsonPath("$.clusters[0].representative.longitude").value(128.6))
                .andExpect(jsonPath("$.clusters[0].count").value(24))
                .andExpect(jsonPath("$.clusters[0].longitude").value(128.61))
                .andExpect(jsonPath("$.clusters[0].nextZoom").value(14))
                .andExpect(jsonPath("$.markers").isEmpty());
    }

    @Test
    void missingRequiredInputAndInvalidEnumsAreBadRequests() throws Exception {
        mvc.perform(get("/admin/places/map")).andExpect(status().isBadRequest());
        mvc.perform(request().param("discoveryStatus","UNKNOWN")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnboundedSizeBeforeServiceInvocation() throws Exception {
        mvc.perform(get("/admin/places/map").param("west","128.5").param("south","35.8")
                .param("east","128.7").param("north","35.95").param("zoom","13")
                .param("width","999999").param("height","800"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void expiredGroupUsesExistingGlobalErrorShape() throws Exception {
        when(service.groupPlaces("expired",1,20)).thenThrow(new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_CLUSTER_INVALID));
        mvc.perform(get("/admin/places/map/clusters/expired/places"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_PLACE_MAP_CLUSTER_INVALID"))
                .andExpect(jsonPath("$.message").isString());
    }
}
