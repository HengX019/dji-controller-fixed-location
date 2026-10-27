package cn.vitamin.mocklocation;

public final class CoordinatesTest {
    private static int count;
    private static void near(double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance) throw new AssertionError(actual + " != " + expected);
        count++;
    }
    private static void rejects(String text, boolean latitude) {
        try { Coordinates.parse(text, latitude); }
        catch (IllegalArgumentException expected) { count++; return; }
        throw new AssertionError("Accepted invalid input: " + text);
    }
    public static void main(String[] args) {
        near(Coordinates.parse(" -90 ", true), -90, 0);
        near(Coordinates.parse("180", false), 180, 0);
        near(Coordinates.parse("−12．5", true), -12.5, 0);
        for (String text : new String[]{"", "NaN", "Infinity", "-Infinity", "abc", "90.0001", "-91"}) rejects(text, true);
        rejects("180.001", false);
        rejects("-181", false);
        double[] direct = Coordinates.toWgs84(22.543096, 114.057865, 0);
        near(direct[0], 22.543096, 0);
        near(direct[1], 114.057865, 0);
        double[] foreign = Coordinates.toWgs84(51.5074, -0.1278, 1);
        near(foreign[0], 51.5074, 0);
        near(foreign[1], -0.1278, 0);
        // Independent widely used Beijing coordinate pair, with rounded input.
        double[] beijing = Coordinates.toWgs84(39.91022649807321, 116.4037135824225, 1);
        near(beijing[0], 39.908823, 0.000003);
        near(beijing[1], 116.397470, 0.000003);
        double[] baidu = Coordinates.toWgs84(39.9165658186413, 116.41008645442567, 2);
        near(baidu[0], 39.908823, 0.000005);
        near(baidu[1], 116.397470, 0.000005);
        // Across China: conversion should converge at normal city coordinates.
        for (double[] point : new double[][]{{22.543096,114.057865},{31.2304,121.4737},{30.5728,104.0668},{43.8256,87.6168}}) {
            double[] gcj = Coordinates.toGcj02(point[0], point[1]);
            double[] back = Coordinates.toWgs84(gcj[0], gcj[1], 1);
            near(back[0], point[0], 1e-7);
            near(back[1], point[1], 1e-7);
        }
        System.out.println("PASS: " + count + " coordinate and input assertions");
    }
}
