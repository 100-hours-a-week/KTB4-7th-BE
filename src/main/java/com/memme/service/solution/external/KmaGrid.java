package com.memme.service.solution.external;

import com.memme.service.store.StoreCoordinates;

public record KmaGrid(int x, int y) {

    private static final double RADIUS = 6371.00877 / 5.0;
    private static final double RADIAN = Math.PI / 180.0;
    private static final double STANDARD_LATITUDE_1 = 30.0 * RADIAN;
    private static final double STANDARD_LATITUDE_2 = 60.0 * RADIAN;
    private static final double ORIGIN_LONGITUDE = 126.0 * RADIAN;
    private static final double ORIGIN_LATITUDE = 38.0 * RADIAN;
    private static final double ORIGIN_X = 43.0;
    private static final double ORIGIN_Y = 136.0;
    private static final double EXPONENT = Math.log(Math.cos(STANDARD_LATITUDE_1)
            / Math.cos(STANDARD_LATITUDE_2))
            / Math.log(Math.tan(Math.PI / 4.0 + STANDARD_LATITUDE_2 / 2.0)
            / Math.tan(Math.PI / 4.0 + STANDARD_LATITUDE_1 / 2.0));
    private static final double SCALE = Math.pow(Math.tan(Math.PI / 4.0 + STANDARD_LATITUDE_1 / 2.0), EXPONENT)
            * Math.cos(STANDARD_LATITUDE_1) / EXPONENT;
    private static final double ORIGIN_RADIUS = RADIUS * SCALE
            / Math.pow(Math.tan(Math.PI / 4.0 + ORIGIN_LATITUDE / 2.0), EXPONENT);

    public static KmaGrid from(StoreCoordinates coordinates) {
        double latitude = coordinates.latitude().doubleValue() * RADIAN;
        double longitude = coordinates.longitude().doubleValue() * RADIAN;
        double radius = RADIUS * SCALE / Math.pow(Math.tan(Math.PI / 4.0 + latitude / 2.0), EXPONENT);
        double theta = longitude - ORIGIN_LONGITUDE;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= EXPONENT;
        return new KmaGrid((int) Math.floor(radius * Math.sin(theta) + ORIGIN_X + 0.5),
                (int) Math.floor(ORIGIN_RADIUS - radius * Math.cos(theta) + ORIGIN_Y + 0.5));
    }
}
