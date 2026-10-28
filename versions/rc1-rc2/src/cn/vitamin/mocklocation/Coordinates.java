package cn.vitamin.mocklocation;

/** Coordinate conversion is entirely local. Public inputs are always latitude, longitude. */
public final class Coordinates {
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    private Coordinates() {}

    public static double parse(String input, boolean latitude) {
        final double value;
        try {
            value = Double.parseDouble(input.trim().replace('\uFF0E', '.').replace('\u2212', '-'));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(latitude ? "请输入有效的纬度" : "请输入有效的经度");
        }
        double limit = latitude ? 90.0 : 180.0;
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > limit) {
            throw new IllegalArgumentException(latitude ? "纬度范围为 -90 到 90" : "经度范围为 -180 到 180");
        }
        return value;
    }

    public static double[] toWgs84(double lat, double lon, int format) {
        if (format < 0 || format > 2) throw new IllegalArgumentException("不支持的坐标类型");
        parse(Double.toString(lat), true);
        parse(Double.toString(lon), false);
        if (format == 0) return new double[]{lat, lon};
        if (format == 2) {
            double x = lon - 0.0065, y = lat - 0.006;
            double xp = Math.PI * 3000.0 / 180.0;
            double z = Math.hypot(x, y) - 0.00002 * Math.sin(y * xp);
            double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * xp);
            lat = z * Math.sin(theta);
            lon = z * Math.cos(theta);
        }
        if (outsideChina(lat, lon)) return new double[]{lat, lon};
        // Invert the commonly used GCJ-02 forward approximation by iteration.
        double wLat = lat, wLon = lon;
        for (int i = 0; i < 10; i++) {
            double[] forward = toGcj02(wLat, wLon);
            double dLat = forward[0] - lat, dLon = forward[1] - lon;
            wLat -= dLat;
            wLon -= dLon;
            if (Math.abs(dLat) < 1e-9 && Math.abs(dLon) < 1e-9) break;
        }
        return new double[]{wLat, wLon};
    }

    static boolean outsideChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    static double[] toGcj02(double lat, double lon) {
        if (outsideChina(lat, lon)) return new double[]{lat, lon};
        double x = lon - 105.0, y = lat - 35.0;
        double dLat = -100.0 + 2*x + 3*y + 0.2*y*y + 0.1*x*y + 0.2*Math.sqrt(Math.abs(x));
        dLat += (20*Math.sin(6*x*Math.PI) + 20*Math.sin(2*x*Math.PI))*2/3;
        dLat += (20*Math.sin(y*Math.PI) + 40*Math.sin(y*Math.PI/3))*2/3;
        dLat += (160*Math.sin(y*Math.PI/12) + 320*Math.sin(y*Math.PI/30))*2/3;
        double dLon = 300.0 + x + 2*y + 0.1*x*x + 0.1*x*y + 0.1*Math.sqrt(Math.abs(x));
        dLon += (20*Math.sin(6*x*Math.PI) + 20*Math.sin(2*x*Math.PI))*2/3;
        dLon += (20*Math.sin(x*Math.PI) + 40*Math.sin(x*Math.PI/3))*2/3;
        dLon += (150*Math.sin(x*Math.PI/12) + 300*Math.sin(x*Math.PI/30))*2/3;
        double rad = lat / 180 * Math.PI;
        double magic = 1 - EE * Math.sin(rad) * Math.sin(rad);
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180 / ((A * (1-EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = dLon * 180 / (A / sqrtMagic * Math.cos(rad) * Math.PI);
        return new double[]{lat + dLat, lon + dLon};
    }
}
