package com.typenull.pingdom.moderation.application.query.place.map;

import static org.assertj.core.api.Assertions.*;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import org.junit.jupiter.api.Test;

class AdminPlaceMapConditionTest {
    @Test
    void rejectsInvalidBoundsNonFiniteValuesZoomAndUnboundedClientSize() {
        for (double[] box : new double[][] {{129,35,128,36}, {128,36,129,35}, {128,35,Double.NaN,36},
                {128,35,Double.POSITIVE_INFINITY,36}, {-181,35,129,36}, {128,-90,129,36}}) {
            assertThatThrownBy(() -> AdminPlaceMapCondition.from(new AdminPlaceMapRequest(
                    box[0], box[1], box[2], box[3], 14, 1200, 800, null, null, null, null)))
                    .isInstanceOf(AdminException.class);
        }
        assertThatThrownBy(() -> AdminPlaceMapCondition.from(new AdminPlaceMapRequest(
                128d,35d,129d,36d,21,1200,800,null,null,null,null))).isInstanceOf(AdminException.class);
        assertThatThrownBy(() -> AdminPlaceMapCondition.from(new AdminPlaceMapRequest(
                128d,35d,129d,36d,14,9000,800,null,null,null,null))).isInstanceOf(AdminException.class);
    }

    @Test
    void matchesExistingCategoryNormalizationAndKeepsAllStatusesByDefault() {
        var c = AdminPlaceMapCondition.from(new AdminPlaceMapRequest(128d,35d,129d,36d,14,1200,800," 카페 ","커피",null,null));
        assertThat(c.keyword()).isEqualTo("카페");
        assertThat(c.category()).isEqualTo("CAFE");
        assertThat(c.discoveryStatus()).isNull();
        assertThat(c.operatingStatus()).isNull();
    }
}
