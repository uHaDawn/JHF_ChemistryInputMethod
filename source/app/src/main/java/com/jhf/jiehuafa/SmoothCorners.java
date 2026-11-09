/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 *
 * 本程序分发时希望它有用，但不提供任何担保，甚至不含适销性或特定用途
 * 适用性的默示担保。详见 GNU 通用公共许可证。
 *
 * 你应已随本程序收到一份 GNU 通用公共许可证副本；如果没有，请见
 * <https://www.gnu.org/licenses/>。
 */
package com.jhf.jiehuafa;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * G2 连续圆角（2026-11-03）。
 *
 * 算法移植自开源项目 Capsule（Kyant0，Apache License 2.0）：
 * https://github.com/Kyant0/Capsule
 *
 * 原实现是 Kotlin + Compose 的 G2Continuity / G2ContinuityProfile，这里逐行
 * 移植到 Android Java：每个角由「半个贝塞尔 + 圆弧 + 半个贝塞尔」组成，
 * 曲率在直线与圆弧之间连续过渡（G2），比普通圆角更接近真实玻璃/金属的倒角。
 *
 * 关键参数与 Capsule 默认值完全一致：
 *   RoundedRectangle: ext=0.5286651, arc=5/9, bezK=1.0732051, arcK=1.0732051
 *   Capsule         : ext=0.5286651*0.75, arc=0, bezK=1, arcK=1
 * 当四角相等且两端正好包住短边时走胶囊分支（横向/纵向/正圆），
 * 与 Capsule 的 AdvancedContinuity.createRoundedRectanglePathSegments 判定一致。
 *
 * 用法：键盘等每帧重绘的地方复用一个 Path 调 {@link #build}；普通 View 背景用
 * {@link SmoothDrawable}。
 */
public final class SmoothCorners {

    // ---- Capsule G2ContinuityProfile.RoundedRectangle ----
    private static final double RR_EXT = 0.5286651;
    private static final double RR_ARC = 5.0 / 9.0;
    private static final double RR_BEZ_K = 1.0732051;
    private static final double RR_ARC_K = 1.0732051;

    // ---- Capsule G2ContinuityProfile.Capsule ----
    private static final double CAP_EXT = 0.5286651 * 0.75;
    private static final double CAP_ARC = 0.0;
    private static final double CAP_BEZ_K = 1.0;
    private static final double CAP_ARC_K = 1.0;

    private static final double PI = Math.PI;
    private static final float EPS = 0.05f;

    /** 圆弧外接矩形复用（仅 UI 线程绘制时使用，避免每帧为每个角分配 RectF）。 */
    private static final RectF ARC_RECT = new RectF();

    private SmoothCorners() {
    }

    /** 四角同半径。 */
    public static Path build(Path out, RectF rect, float radius) {
        return build(out, rect, radius, radius, radius, radius);
    }

    /**
     * 生成圆角矩形路径（坐标已平移到 rect）。
     *
     * @param out   复用的 Path（会先 reset）
     * @param rect  目标矩形
     * @param tl    左上圆角
     * @param tr    右上圆角
     * @param br    右下圆角
     * @param bl    左下圆角
     */
    public static Path build(Path out, RectF rect,
                             float tl, float tr, float br, float bl) {
        out.reset();
        float w = rect.width();
        float h = rect.height();
        if (w <= 0f || h <= 0f) return out;

        float max = Math.min(w, h) * 0.5f;
        float a = clampRadius(tl, max);
        float b = clampRadius(tr, max);
        float c = clampRadius(br, max);
        float d = clampRadius(bl, max);

        if (a <= 0f && b <= 0f && c <= 0f && d <= 0f) {
            out.addRect(rect, Path.Direction.CW);
            return out;
        }

        if (isCapsule(w, h, a, b, c, d)) {
            if (w > h + EPS) {
                horizontalCapsule(out, w, h);
            } else if (h > w + EPS) {
                verticalCapsule(out, w, h);
            } else {
                out.addCircle(w * 0.5f, h * 0.5f, w * 0.5f, Path.Direction.CW);
            }
        } else {
            standard(out, w, h, a, b, c, d);
        }
        out.offset(rect.left, rect.top);
        return out;
    }

    /** Capsule 判定：四角相等，且（上两角之和 == 宽 或 左两角之和 == 高）。 */
    private static boolean isCapsule(float w, float h, float tl, float tr, float br, float bl) {
        boolean all = Math.abs(tl - tr) < EPS && Math.abs(br - bl) < EPS && Math.abs(tl - br) < EPS;
        if (!all) return false;
        return Math.abs(tl + tr - w) < EPS || Math.abs(tl + bl - h) < EPS;
    }

    private static float clampRadius(float r, float max) {
        if (r <= 0f) return 0f;
        return r > max ? max : r;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 1.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------
    // 标准圆角矩形（Capsule G2Continuity.createStandardRoundedRectanglePathSegments）
    // ------------------------------------------------------------------

    private static void standard(Path path, double width, double height,
                                 double tl, double tr, double br, double bl) {
        double centerX = width * 0.5;
        double centerY = height * 0.5;

        // 每个半角相对「胶囊 → 安全圆角矩形」的比例（0 胶囊，1 普通安全圆角）
        double rTLV = clamp01((centerY / tl - 1.0) / RR_EXT);
        double rTLH = clamp01((centerX / tl - 1.0) / RR_EXT);
        double rTRH = clamp01((centerX / tr - 1.0) / RR_EXT);
        double rTRV = clamp01((centerY / tr - 1.0) / RR_EXT);
        double rBRV = clamp01((centerY / br - 1.0) / RR_EXT);
        double rBRH = clamp01((centerX / br - 1.0) / RR_EXT);
        double rBLH = clamp01((centerX / bl - 1.0) / RR_EXT);
        double rBLV = clamp01((centerY / bl - 1.0) / RR_EXT);

        double ratioTL = Math.min(rTLV, rTLH);
        double ratioTR = Math.min(rTRH, rTRV);
        double ratioBR = Math.min(rBRV, rBRH);
        double ratioBL = Math.min(rBLH, rBLV);

        double extTL = lerp(CAP_EXT, RR_EXT, ratioTL);
        double extTR = lerp(CAP_EXT, RR_EXT, ratioTR);
        double extBR = lerp(CAP_EXT, RR_EXT, ratioBR);
        double extBL = lerp(CAP_EXT, RR_EXT, ratioBL);

        double extTLV = extTL * rTLV, extTLH = extTL * rTLH;
        double extTRH = extTR * rTRH, extTRV = extTR * rTRV;
        double extBRV = extBR * rBRV, extBRH = extBR * rBRH;
        double extBLH = extBL * rBLH, extBLV = extBL * rBLV;

        double offsetTLV = -tl * extTLV, offsetTLH = -tl * extTLH;
        double offsetTRH = -tr * extTRH, offsetTRV = -tr * extTRV;
        double offsetBRV = -br * extBRV, offsetBRH = -br * extBRH;
        double offsetBLH = -bl * extBLH, offsetBLV = -bl * extBLV;

        double kTLV = lerp(CAP_BEZ_K, RR_BEZ_K, rTLV), kTLH = lerp(CAP_BEZ_K, RR_BEZ_K, rTLH);
        double kTRH = lerp(CAP_BEZ_K, RR_BEZ_K, rTRH), kTRV = lerp(CAP_BEZ_K, RR_BEZ_K, rTRV);
        double kBRV = lerp(CAP_BEZ_K, RR_BEZ_K, rBRV), kBRH = lerp(CAP_BEZ_K, RR_BEZ_K, rBRH);
        double kBLH = lerp(CAP_BEZ_K, RR_BEZ_K, rBLH), kBLV = lerp(CAP_BEZ_K, RR_BEZ_K, rBLV);

        double arcTL = lerp(CAP_ARC, RR_ARC, ratioTL), arcTR = lerp(CAP_ARC, RR_ARC, ratioTR);
        double arcBR = lerp(CAP_ARC, RR_ARC, ratioBR), arcBL = lerp(CAP_ARC, RR_ARC, ratioBL);

        double aKTL = 1.0 + (RR_ARC_K - 1.0) * ratioTL;
        double aKTR = 1.0 + (RR_ARC_K - 1.0) * ratioTR;
        double aKBR = 1.0 + (RR_ARC_K - 1.0) * ratioBR;
        double aKBL = 1.0 + (RR_ARC_K - 1.0) * ratioBL;

        Bez bTLV = bezier(extTLV, arcTL, kTLV, aKTL);
        Bez bTLH = bezier(extTLH, arcTL, kTLH, aKTL);
        Bez bTRH = bezier(extTRH, arcTR, kTRH, aKTR);
        Bez bTRV = bezier(extTRV, arcTR, kTRV, aKTR);
        Bez bBRV = bezier(extBRV, arcBR, kBRV, aKBR);
        Bez bBRH = bezier(extBRH, arcBR, kBRH, aKBR);
        Bez bBLH = bezier(extBLH, arcBL, kBLH, aKBL);
        Bez bBLV = bezier(extBLV, arcBL, kBLV, aKBL);

        // 起点：PathSegments.toPath() 用首段的 from 作为 moveTo
        double x = 0.0;
        double y = tl;
        path.moveTo((float) x, (float) (y - offsetTLV));

        // 左上：竖向半贝塞尔 → 圆弧 → 横向半贝塞尔
        if (tl > 0.0) {
            cubic(path,
                    x + bTLV.p1y * tl, y - bTLV.p1x * tl,
                    x + bTLV.p2y * tl, y - bTLV.p2x * tl,
                    x + bTLV.p3y * tl, y - bTLV.p3x * tl);
            arcScaled(path, tl, tl, tl, 1.0 / aKTL,
                    PI + PI * 0.5 * (1.0 - arcTL) * 0.5, PI * 0.5 * arcTL);
            x = tl;
            y = 0.0;
            cubic(path,
                    x - bTLH.p2x * tl, y + bTLH.p2y * tl,
                    x - bTLH.p1x * tl, y + bTLH.p1y * tl,
                    x - Math.max(bTLH.p0x * tl, offsetTLH), y + bTLH.p0y * tl);
        }

        // 上边
        x = width - tr;
        y = 0.0;
        path.lineTo((float) (x + offsetTRH), (float) y);

        // 右上
        if (tr > 0.0) {
            cubic(path,
                    x + bTRH.p1x * tr, y + bTRH.p1y * tr,
                    x + bTRH.p2x * tr, y + bTRH.p2y * tr,
                    x + bTRH.p3x * tr, y + bTRH.p3y * tr);
            arcScaled(path, width - tr, tr, tr, 1.0 / aKTR,
                    -PI * 0.5 + PI * 0.5 * (1.0 - arcTR) * 0.5, PI * 0.5 * arcTR);
            x = width;
            y = tr;
            cubic(path,
                    x - bTRV.p2y * tr, y - bTRV.p2x * tr,
                    x - bTRV.p1y * tr, y - bTRV.p1x * tr,
                    x - bTRV.p0y * tr, y - Math.max(bTRV.p0x * tr, offsetTRV));
        }

        // 右边
        x = width;
        y = height - br;
        path.lineTo((float) x, (float) (y + offsetBRV));

        // 右下
        if (br > 0.0) {
            cubic(path,
                    x - bBRV.p1y * br, y + bBRV.p1x * br,
                    x - bBRV.p2y * br, y + bBRV.p2x * br,
                    x - bBRV.p3y * br, y + bBRV.p3x * br);
            arcScaled(path, width - br, height - br, br, 1.0 / aKBR,
                    PI * 0.5 * (1.0 - arcBR) * 0.5, PI * 0.5 * arcBR);
            x = width - br;
            y = height;
            cubic(path,
                    x + bBRH.p2x * br, y - bBRH.p2y * br,
                    x + bBRH.p1x * br, y - bBRH.p1y * br,
                    x + Math.max(bBRH.p0x * br, offsetBRH), y - bBRH.p0y * br);
        }

        // 下边
        x = bl;
        y = height;
        path.lineTo((float) (x - offsetBLH), (float) y);

        // 左下
        if (bl > 0.0) {
            cubic(path,
                    x - bBLH.p1x * bl, y - bBLH.p1y * bl,
                    x - bBLH.p2x * bl, y - bBLH.p2y * bl,
                    x - bBLH.p3x * bl, y - bBLH.p3y * bl);
            arcScaled(path, bl, height - bl, bl, 1.0 / aKBL,
                    PI * 0.5 + PI * 0.5 * (1.0 - arcBL) * 0.5, PI * 0.5 * arcBL);
            x = 0.0;
            y = height - bl;
            cubic(path,
                    x + bBLV.p2y * bl, y + bBLV.p2x * bl,
                    x + bBLV.p1y * bl, y + bBLV.p1x * bl,
                    x + bBLV.p0y * bl, y + Math.max(bBLV.p0x * bl, offsetBLV));
        }

        path.close();
    }

    // ------------------------------------------------------------------
    // 胶囊（Capsule createHorizontalCapsulePathSegments / Vertical）
    // ------------------------------------------------------------------

    private static void horizontalCapsule(Path path, double width, double height) {
        double radius = height * 0.5;
        double centerX = width * 0.5;

        double ratioH = clamp01((centerX / radius - 1.0) / CAP_EXT);
        double extH = CAP_EXT * ratioH;
        double offsetH = -radius * extH;
        double kH = lerp(CAP_BEZ_K, RR_BEZ_K, ratioH);
        Bez b = bezier(extH, CAP_ARC, kH, 1.0);

        double arcRad = PI * 0.5 * CAP_ARC;
        double bezRad = (PI * 0.5 - arcRad) * 0.5;
        double sweepRad = (bezRad + arcRad) * 2.0;

        // 首段 Arc 的 from 即真实起点
        double start = PI * 0.5 + bezRad;
        path.moveTo((float) (radius + Math.cos(start) * radius),
                (float) (radius + Math.sin(start) * radius));

        // 左端圆弧
        ARC_RECT.set(0f, 0f, (float) (2 * radius), (float) (2 * radius));
        path.arcTo(ARC_RECT, (float) Math.toDegrees(start), (float) Math.toDegrees(sweepRad));

        // 左上横贝塞尔
        double x = radius, y = 0.0;
        cubic(path,
                x - b.p2x * radius, y + b.p2y * radius,
                x - b.p1x * radius, y + b.p1y * radius,
                x - Math.max(b.p0x * radius, offsetH), y + b.p0y * radius);

        // 上边
        x = width - radius;
        y = 0.0;
        path.lineTo((float) (x + offsetH), (float) y);

        // 右上横贝塞尔
        cubic(path,
                x + b.p1x * radius, y + b.p1y * radius,
                x + b.p2x * radius, y + b.p2y * radius,
                x + b.p3x * radius, y + b.p3y * radius);

        // 右端圆弧
        double rStart = -(PI * 0.5 - bezRad);
        ARC_RECT.set((float) (width - 2 * radius), 0f, (float) width, (float) (2 * radius));
        path.arcTo(ARC_RECT, (float) Math.toDegrees(rStart), (float) Math.toDegrees(sweepRad));

        // 右下横贝塞尔
        x = width - radius;
        y = height;
        cubic(path,
                x + b.p2x * radius, y - b.p2y * radius,
                x + b.p1x * radius, y - b.p1y * radius,
                x + Math.max(b.p0x * radius, offsetH), y - b.p0y * radius);

        // 下边
        x = radius;
        y = height;
        path.lineTo((float) (x - offsetH), (float) y);

        // 左下横贝塞尔
        cubic(path,
                x - b.p1x * radius, y - b.p1y * radius,
                x - b.p2x * radius, y - b.p2y * radius,
                x - b.p3x * radius, y - b.p3y * radius);

        path.close();
    }

    private static void verticalCapsule(Path path, double width, double height) {
        double radius = width * 0.5;
        double centerY = height * 0.5;

        double ratioV = clamp01((centerY / radius - 1.0) / CAP_EXT);
        double extV = CAP_EXT * ratioV;
        double offsetV = -radius * extV;
        double kV = lerp(CAP_BEZ_K, RR_BEZ_K, ratioV);
        Bez b = bezier(extV, CAP_ARC, kV, 1.0);

        double arcRad = PI * 0.5 * CAP_ARC;
        double bezRad = (PI * 0.5 - arcRad) * 0.5;
        double sweepRad = (bezRad + arcRad) * 2.0;

        // 首段 TLV：from 为 moveTo 点
        double x = 0.0, y = radius;
        path.moveTo((float) x, (float) (y - offsetV));

        cubic(path,
                x + b.p1y * radius, y - b.p1x * radius,
                x + b.p2y * radius, y - b.p2x * radius,
                x + b.p3y * radius, y - b.p3x * radius);

        // 顶端圆弧
        double tStart = -(PI - bezRad);
        ARC_RECT.set(0f, 0f, (float) (2 * radius), (float) (2 * radius));
        path.arcTo(ARC_RECT, (float) Math.toDegrees(tStart), (float) Math.toDegrees(sweepRad));

        // 右上竖贝塞尔
        x = width;
        y = radius;
        cubic(path,
                x - b.p2y * radius, y - b.p2x * radius,
                x - b.p1y * radius, y - b.p1x * radius,
                x - b.p0y * radius, y - Math.max(b.p0x * radius, offsetV));

        // 右边
        x = width;
        y = height - radius;
        path.lineTo((float) x, (float) (y + offsetV));

        // 右下竖贝塞尔
        cubic(path,
                x - b.p1y * radius, y + b.p1x * radius,
                x - b.p2y * radius, y + b.p2x * radius,
                x - b.p3y * radius, y + b.p3x * radius);

        // 底端圆弧
        double bStart = bezRad;
        ARC_RECT.set((float) (width - 2 * radius), (float) (height - 2 * radius),
                (float) width, (float) height);
        path.arcTo(ARC_RECT, (float) Math.toDegrees(bStart), (float) Math.toDegrees(sweepRad));

        // 左下竖贝塞尔
        x = 0.0;
        y = height - radius;
        cubic(path,
                x + b.p2y * radius, y + b.p2x * radius,
                x + b.p1y * radius, y + b.p1x * radius,
                x + b.p0y * radius, y + Math.max(b.p0x * radius, offsetV));

        path.close();
    }

    // ------------------------------------------------------------------
    // 贝塞尔与圆弧工具（对应 Capsule G2ContinuityProfile / PathSegment.Arc）
    // ------------------------------------------------------------------

    /** 三次贝塞尔四点的归一化坐标（未乘圆角半径）。 */
    private static final class Bez {
        double p0x, p0y, p1x, p1y, p2x, p2y, p3x, p3y;
    }

    private static Bez bezier(double extendedFraction, double arcFraction,
                              double bezierCurvatureScale, double arcCurvatureScale) {
        double arcRadians = PI * 0.5 * arcFraction;
        double bezierRadians = (PI * 0.5 - arcRadians) * 0.5;
        double sin = Math.sin(bezierRadians);
        double cos = Math.cos(bezierRadians);

        if (bezierCurvatureScale == 1.0 && arcCurvatureScale == 1.0) {
            double halfTan = sin / (1.0 + cos);
            return cubicBezier(
                    -extendedFraction, 0.0,
                    (1.0 - 1.5 / (1.0 + cos)) * halfTan, 0.0,
                    halfTan, 0.0,
                    sin, 1.0 - cos);
        }

        double radiusScale = 1.0 / arcCurvatureScale;
        double arcCenterX = 0.0 + (1.0 / Math.sqrt(2.0)) * (1.0 - radiusScale);
        double arcCenterY = 1.0 + (-1.0 / Math.sqrt(2.0)) * (1.0 - radiusScale);
        double arcStartX = arcCenterX + sin * radiusScale;
        double arcStartY = arcCenterY + (-cos) * radiusScale;

        return zeroStartCurvatureBezier(
                -extendedFraction, 0.0,
                arcStartX, arcStartY,
                1.0, 0.0,
                cos, sin,
                bezierCurvatureScale);
    }

    /**
     * Capsule generateG2ContinuousBezierWithZeroStartCurvature：
     * 起点曲率为 0、终点曲率由 endCurvature 指定，两条切线已知。
     */
    private static Bez zeroStartCurvatureBezier(
            double startX, double startY, double endX, double endY,
            double stx, double sty, double etx, double ety, double endCurvature) {
        double a2 = 1.5 * endCurvature;
        double b = stx * ety - sty * etx;
        double dx = endX - startX;
        double dy = endY - startY;
        double c1 = -dy * stx + dx * sty;
        double c2 = dy * etx - dx * ety;

        double lambda0 = -c2 / b - a2 * c1 * c1 / b / b / b;
        double lambda3 = -c1 / b;

        Bez bez = new Bez();
        bez.p0x = startX;
        bez.p0y = startY;
        bez.p1x = startX + Math.max(lambda0 * stx, 0.0);
        bez.p1y = startY + Math.max(lambda0 * sty, 0.0);
        bez.p2x = endX - Math.max(lambda3 * etx, 0.0);
        bez.p2y = endY - Math.max(lambda3 * ety, 0.0);
        bez.p3x = endX;
        bez.p3y = endY;
        return bez;
    }

    private static Bez cubicBezier(double p0x, double p0y,
                                   double p1x, double p1y,
                                   double p2x, double p2y,
                                   double p3x, double p3y) {
        Bez b = new Bez();
        b.p0x = p0x; b.p0y = p0y;
        b.p1x = p1x; b.p1y = p1y;
        b.p2x = p2x; b.p2y = p2y;
        b.p3x = p3x; b.p3y = p3y;
        return b;
    }

    private static void cubic(Path path, double x1, double y1,
                              double x2, double y2, double x3, double y3) {
        path.cubicTo((float) x1, (float) y1, (float) x2, (float) y2,
                (float) x3, (float) y3);
    }

    /**
     * Capsule PathSegmentsBuilder.arcToWithScaledRadius：圆弧半径按 arcKScale 缩放，
     * 圆心沿角平分线向里挪，保证与两端贝塞尔相接。
     */
    private static void arcScaled(Path path, double cx, double cy, double radius,
                                  double radiusScale, double startAngle, double sweepAngle) {
        double centerAngle = startAngle + sweepAngle * 0.5;
        double nx = cx + Math.cos(centerAngle) * radius * (1.0 - radiusScale);
        double ny = cy + Math.sin(centerAngle) * radius * (1.0 - radiusScale);
        float r = (float) (radius * radiusScale);
        ARC_RECT.set((float) (nx - r), (float) (ny - r), (float) (nx + r), (float) (ny + r));
        path.arcTo(ARC_RECT,
                (float) Math.toDegrees(startAngle), (float) Math.toDegrees(sweepAngle));
    }
}
