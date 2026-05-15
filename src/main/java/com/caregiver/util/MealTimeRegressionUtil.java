package com.caregiver.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class MealTimeRegressionUtil {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MINUTES_PER_DAY = 24 * 60;

    private MealTimeRegressionUtil() {
    }

    public static int timeToMinute(String time) {
        LocalTime localTime = LocalTime.parse(time, TIME_FMT);
        return localTime.getHour() * 60 + localTime.getMinute();
    }

    public static String minuteToTime(int minute) {
        int normalizedMinute = Math.max(0, Math.min(minute, MINUTES_PER_DAY - 1));
        return LocalTime.of(normalizedMinute / 60, normalizedMinute % 60).format(TIME_FMT);
    }

    /**
     * Robust typical meal time from a list of "minutes since midnight" values.
     * <p>
     * Previously this used linear regression on record index vs. minute, then extrapolated to
     * "index n+1". That is not a valid model for meal habits (append order is not a linear time
     * axis), and produced absurd predictions when the series had drift or outliers.
     */
    public static int medianMinute(List<Integer> mealMinutes) {
        if (mealMinutes == null || mealMinutes.isEmpty()) {
            throw new IllegalArgumentException("Meal minutes cannot be empty");
        }
        List<Integer> sorted = new ArrayList<>(mealMinutes);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (int) Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
    }

    /**
     * Linear regression y ≈ a*x + b over real-valued day offsets x (e.g. days since anchor date).
     * Predicts y at {@code xPredict}. Empty when the design matrix is degenerate (e.g. all x equal).
     */
    public static OptionalInt predictLinearMinute(List<Double> xDays, List<Integer> yMinutes, double xPredict) {
        if (xDays == null || yMinutes == null || xDays.size() != yMinutes.size()) {
            throw new IllegalArgumentException("xDays and yMinutes must be same-length lists");
        }
        int n = xDays.size();
        if (n < 2) {
            return OptionalInt.empty();
        }
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = xDays.get(i);
            double y = yMinutes.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-6) {
            return OptionalInt.empty();
        }
        double a = (n * sumXY - sumX * sumY) / denominator;
        double b = (sumY - a * sumX) / n;
        return OptionalInt.of((int) Math.round(a * xPredict + b));
    }

    /**
     * Quadratic least squares: y ≈ c0 + c1*x + c2*x^2 (same x as day offsets).
     * Higher degrees are intentionally not used: few samples + extrapolation makes high-order
     * polynomials unstable (Runge / wild tails).
     */
    public static final class QuadraticFit {
        private final double c0;
        private final double c1;
        private final double c2;

        public QuadraticFit(double c0, double c1, double c2) {
            this.c0 = c0;
            this.c1 = c1;
            this.c2 = c2;
        }

        public double predict(double x) {
            return c0 + c1 * x + c2 * x * x;
        }

        /** Curvature term (minutes per day^2); large |c2| means aggressive bend when extrapolating. */
        public double getC2() {
            return c2;
        }
    }

    /**
     * Fits y ≈ c0 + c1*x + c2*x^2 by ordinary least squares (normal equations, 3×3).
     */
    public static Optional<QuadraticFit> fitQuadraticLeastSquares(List<Double> xDays, List<Integer> yMinutes) {
        if (xDays == null || yMinutes == null || xDays.size() != yMinutes.size()) {
            throw new IllegalArgumentException("xDays and yMinutes must be same-length lists");
        }
        int n = xDays.size();
        if (n < 3) {
            return Optional.empty();
        }
        double sum1 = 0;
        double sum2 = 0;
        double sum3 = 0;
        double sum4 = 0;
        double t0 = 0;
        double t1 = 0;
        double t2 = 0;
        for (int i = 0; i < n; i++) {
            double x = xDays.get(i);
            double y = yMinutes.get(i);
            double x2 = x * x;
            double x3 = x2 * x;
            double x4 = x3 * x;
            sum1 += x;
            sum2 += x2;
            sum3 += x3;
            sum4 += x4;
            t0 += y;
            t1 += x * y;
            t2 += x2 * y;
        }
        double[][] aug = new double[][] {
                {n, sum1, sum2, t0},
                {sum1, sum2, sum3, t1},
                {sum2, sum3, sum4, t2}
        };
        double[] c = new double[3];
        if (!gaussJordan3(aug, c)) {
            return Optional.empty();
        }
        if (!Double.isFinite(c[0]) || !Double.isFinite(c[1]) || !Double.isFinite(c[2])) {
            return Optional.empty();
        }
        return Optional.of(new QuadraticFit(c[0], c[1], c[2]));
    }

    /**
     * Gauss–Jordan elimination on a 3×4 augmented matrix; writes solution into {@code out} (length 3).
     */
    private static boolean gaussJordan3(double[][] a, double[] out) {
        final int n = 3;
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(a[pivot][col]) < 1e-10) {
                return false;
            }
            if (pivot != col) {
                double[] tmp = a[col];
                a[col] = a[pivot];
                a[pivot] = tmp;
            }
            double div = a[col][col];
            for (int j = col; j <= n; j++) {
                a[col][j] /= div;
            }
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                double f = a[r][col];
                if (Math.abs(f) < 1e-14) {
                    continue;
                }
                for (int j = col; j <= n; j++) {
                    a[r][j] -= f * a[col][j];
                }
            }
        }
        out[0] = a[0][3];
        out[1] = a[1][3];
        out[2] = a[2][3];
        return true;
    }
}
