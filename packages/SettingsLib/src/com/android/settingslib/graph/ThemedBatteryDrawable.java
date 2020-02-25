package com.android.settingslib.graph;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.Op;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.PathParser;
import android.util.TypedValue;

import com.android.settingslib.R;
import com.android.settingslib.Utils;

public class ThemedBatteryDrawable extends BatteryMeterDrawableBase {
    private int backgroundColor = 0xFFFF00FF;
    private final Path boltPath = new Path();
    private boolean charging;
    private int[] colorLevels;
    private final Context context;
    private int criticalLevel;
    private boolean dualTone;
    private int fillColor = 0xFFFF00FF;
    private final Path fillMask = new Path();
    private final RectF fillRect = new RectF();
    private int intrinsicHeight;
    private int intrinsicWidth;
    private boolean invertFillIcon;
    private int levelColor = 0xFFFF00FF;
    private final Path levelPath = new Path();
    private final RectF levelRect = new RectF();
    private final Rect padding = new Rect();
    private final Path errorPerimeterPath = new Path();
    private final Path perimeterPath = new Path();
    private final Path plusPath = new Path();
    private boolean powerSaveEnabled;
    private final Matrix scaleMatrix = new Matrix();
    private final Path scaledBolt = new Path();
    private final Path scaledFill = new Path();
    private final Path scaledErrorPerimeter = new Path();
    private final Path scaledPerimeter = new Path();
    private final Path scaledPlus = new Path();
    private final Path unifiedPath = new Path();
    private final Path textPath = new Path();
    private final RectF iconRect = new RectF();

    private final Paint dualToneBackgroundFill;
    private final Paint fillColorStrokePaint;
    private final Paint fillColorStrokeProtection;
    private final Paint fillPaint;
    private final Paint textPaint;
    private final Paint errorPaint;

    private final float mWidthDp = 12f;
    private final float mHeightDp = 21f;

    private int level;
    private boolean showPercent;

    private final Paint getDualToneBackgroundFill() {
        return this.dualToneBackgroundFill;
    }

    private final Paint getFillColorStrokePaint() {
        return this.fillColorStrokePaint;
    }

    private final Paint getFillColorStrokeProtection() {
        return this.fillColorStrokeProtection;
    }

    private final Paint getErrorPaint() {
        return this.errorPaint;
    }

    private final Paint getFillPaint() {
        return this.fillPaint;
    }

    private final Paint getTextPaint() {
        return this.textPaint;
    }

    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    public void setAlpha(int i) {
    }

    public ThemedBatteryDrawable(Context context, int frameColor) {
        super(context, frameColor);

        this.context = context;
        float f = this.context.getResources().getDisplayMetrics().density;
        this.intrinsicHeight = (int) (mHeightDp * f);
        this.intrinsicWidth = (int) (mWidthDp * f);
        Resources res = this.context.getResources();

        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);

        final int N = levels.length();
        colorLevels = new int[2 * N];
        for (int i = 0; i < N; i++) {
            colorLevels[2 * i] = levels.getInt(i, 0);
            if (colors.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                colorLevels[2 * i + 1] = Utils.getColorAttrDefaultColor(this.context, colors.getThemeAttributeId(i, 0));
            } else {
                colorLevels[2 * i + 1] = colors.getColor(i, 0);
            }
        }
        levels.recycle();
        colors.recycle();

        setCriticalLevel(res.getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel));

        dualToneBackgroundFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        dualToneBackgroundFill.setColor(frameColor);
        dualToneBackgroundFill.setAlpha(255);
        dualToneBackgroundFill.setDither(true);
        dualToneBackgroundFill.setStrokeWidth(0f);
        dualToneBackgroundFill.setStyle(Style.FILL_AND_STROKE);

        fillColorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillColorStrokePaint.setColor(frameColor);
        fillColorStrokePaint.setDither(true);
        fillColorStrokePaint.setStrokeWidth(5f);
        fillColorStrokePaint.setStyle(Style.STROKE);
        fillColorStrokePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        fillColorStrokePaint.setStrokeMiter(5f);
        fillColorStrokePaint.setStrokeJoin(Join.ROUND);

        fillColorStrokeProtection = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillColorStrokeProtection.setDither(true);
        fillColorStrokeProtection.setStrokeWidth(5f);
        fillColorStrokeProtection.setStyle(Style.STROKE);
        fillColorStrokeProtection.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        fillColorStrokeProtection.setStrokeMiter(5f);
        fillColorStrokeProtection.setStrokeJoin(Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(frameColor);
        fillPaint.setAlpha(255);
        fillPaint.setDither(true);
        fillPaint.setStrokeWidth(0f);
        fillPaint.setStyle(Style.FILL_AND_STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);

        errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        errorPaint.setColor(Utils.getColorStateListDefaultColor(context, R.color.batterymeter_plus_color));
        errorPaint.setAlpha(255);
        errorPaint.setDither(true);
        errorPaint.setStrokeWidth(0f);
        errorPaint.setStyle(Style.FILL_AND_STROKE);
        errorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));

        loadPaths();
    }

    public void setCriticalLevel(int i) {
        this.criticalLevel = i;
    }

    public final void setCharging(boolean charging) {
        if (this.charging != charging) {
            this.charging = charging;
            super.setCharging(charging);
        }
    }

    public boolean getCharging() {
        return this.charging;
    }

    public final boolean getPowerSaveEnabled() {
        return this.powerSaveEnabled;
    }

    public final void setPowerSaveEnabled(boolean enabled) {
        if (this.powerSaveEnabled != enabled) {
            this.powerSaveEnabled = enabled;
            super.setPowerSave(enabled);
        }
    }

    public void setShowPercent(boolean show) {
        if(this.showPercent != show) {
            this.showPercent = show;
            super.setShowPercent(show);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (getMeterStyle() != BATTERY_STYLE_Q) {
            super.draw(canvas);
            return;
        }
        boolean opaqueBolt = level <= 30;
        boolean drawText;
        float pctX = 0, pctY = 0, textHeight;
        String pctText = null;
        boolean pctOpaque;
        if (!this.charging && !this.powerSaveEnabled && this.showPercent) {
            float baseHeight = (this.dualTone ? this.iconRect : this.fillRect).height();
            this.textPaint.setColor(getColorForLevel(this.level));
            final float full = 0.38f;
            final float nofull = 0.5f;
            final float single = 0.75f;
            this.textPaint.setTextSize(baseHeight * (this.level == 100 ? full : nofull));
            textHeight = -mTextPaint.getFontMetrics().ascent;
            pctText = String.valueOf(this.level);
            pctX = this.fillRect.width() * 0.5f + this.fillRect.left;
            pctY = (this.fillRect.height() + textHeight) * 0.47f + this.fillRect.top;
            this.textPath.reset();
            this.textPaint.getTextPath(pctText, 0, pctText.length(), pctX, pctY, this.textPath);
            drawText = true;
        } else {
            drawText = false;
        }

        canvas.saveLayer(null, null);
        this.unifiedPath.reset();
        this.levelPath.reset();
        this.levelRect.set(this.fillRect);
        float fillFraction  = ((float) this.level) / 100.0f;
        float levelTop;
        if (this.level >= 95) {
            levelTop = this.fillRect.top;
        } else {
            RectF rectF = this.fillRect;
            levelTop = (rectF.height() * (((float) 1) - fillFraction)) + rectF.top;
        }
        pctOpaque = levelTop > pctY;
        this.levelRect.top = (float) Math.floor(this.dualTone ? this.fillRect.top : (double) levelTop);
        this.levelPath.addRect(this.levelRect, Direction.CCW);
        this.unifiedPath.addPath(this.scaledPerimeter);
        this.unifiedPath.op(this.levelPath, Op.UNION);
        getFillPaint().setColor(this.levelColor);
        if (this.charging) {
            if (!this.dualTone || !opaqueBolt) {
                this.unifiedPath.op(this.scaledBolt, Op.DIFFERENCE);
            }
            if (!this.dualTone && !this.invertFillIcon) {
                canvas.drawPath(this.scaledBolt, getFillPaint());
            }
        } else if (drawText) {
            if (!this.dualTone || !pctOpaque) {
                this.unifiedPath.op(this.textPath, Op.DIFFERENCE);
            }
            if (!this.dualTone && !this.invertFillIcon) {
                canvas.drawPath(this.textPath, getFillPaint());
            }
        }
        if (this.dualTone) {
            canvas.drawPath(this.unifiedPath, getDualToneBackgroundFill());
            canvas.save();
            float clipTop = getBounds().bottom - getBounds().height() * fillFraction;
            canvas.clipRect(0f, clipTop, (float) getBounds().right, (float) getBounds().bottom);
            canvas.drawPath(this.unifiedPath, getFillPaint());
            if (this.charging && opaqueBolt) {
                canvas.drawPath(this.scaledBolt, getFillPaint());
            } else if (drawText && pctOpaque) {
                canvas.drawPath(this.textPath, getFillPaint());
            }
            canvas.restore();
        } else {
            // Non dual-tone means we draw the perimeter (with the level fill), and potentially
            // draw the fill again with a critical color
            getFillPaint().setColor(this.fillColor);
            canvas.drawPath(this.unifiedPath, getFillPaint());
            getFillPaint().setColor(this.levelColor);

            // Show colorError below this level
            if (this.level <= 15 && !this.charging) {
                canvas.save();
                canvas.clipPath(this.scaledFill);
                canvas.drawPath(this.levelPath, getFillPaint());
                canvas.restore();
            }
        }
        if (this.charging) {
            canvas.clipOutPath(this.scaledBolt);
            if (this.invertFillIcon) {
                canvas.drawPath(this.scaledBolt, getFillColorStrokePaint());
            } else {
                canvas.drawPath(this.scaledBolt, getFillColorStrokeProtection());
            }
        } else if (this.powerSaveEnabled) {
            // If power save is enabled draw the perimeter path with colorError
            canvas.drawPath(this.scaledErrorPerimeter, getErrorPaint());
            // And draw the plus sign on top of the fill
            canvas.drawPath(this.scaledPlus, getErrorPaint());
        } else if (drawText) {
            canvas.clipOutPath(this.textPath);
            if (this.invertFillIcon) {
                canvas.drawPath(this.textPath, getFillColorStrokePaint());
            } else {
                canvas.drawPath(this.textPath, getFillColorStrokeProtection());
            }
        }
        canvas.restore();
    }

    public int getBatteryLevel() {
        return this.level;
    }

    protected int batteryColorForLevel(int level) {
        return (this.charging || this.powerSaveEnabled)
                ? this.fillColor
                : getColorForLevel(level);
    }

    private final int getColorForLevel(int percent) {
        int thresh, color = 0;
        for (int i = 0; i < colorLevels.length; i += 2) {
            thresh = colorLevels[i];
            color = colorLevels[i + 1];
            if (percent <= thresh) {

                // Respect tinting for "normal" level
                if (i == colorLevels.length - 2) {
                    return this.fillColor;
                } else {
                    return color;
                }
            }
        }
        return color;
    }

    public void setColorFilter(ColorFilter colorFilter) {
        getFillPaint().setColorFilter(colorFilter);
        getFillColorStrokePaint().setColorFilter(colorFilter);
        getDualToneBackgroundFill().setColorFilter(colorFilter);
    }

    public int getIntrinsicHeight() {
        if (getMeterStyle() == BATTERY_STYLE_Q) {
            return this.intrinsicHeight;
        } else {
            return super.getIntrinsicHeight();
        }
    }

    public int getIntrinsicWidth() {
        if (getMeterStyle() == BATTERY_STYLE_Q) {
            return this.intrinsicWidth;
        } else {
            return super.getIntrinsicWidth();
        }
    }

    public void setBatteryLevel(int val) {
        if (this.level != val) {
            this.level = val;
            this.invertFillIcon = val >= 67 ? true : val <= 33 ? false : this.invertFillIcon;
            this.levelColor = batteryColorForLevel(this.level);
            super.setBatteryLevel(val);
        }
    }

    protected void onBoundsChange(Rect rect) {
        super.onBoundsChange(rect);
        updateSize();
    }

    public void setColors(int fillColor, int backgroundColor, int singleToneColor) {
        this.fillColor = this.dualTone ? fillColor : singleToneColor;
        getFillPaint().setColor(this.fillColor);
        getFillColorStrokePaint().setColor(this.fillColor);
        this.backgroundColor = backgroundColor;
        getDualToneBackgroundFill().setColor(backgroundColor);
        this.levelColor = batteryColorForLevel(this.level);
        super.setColors(fillColor, backgroundColor);
    }

    private final void updateSize() {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            this.scaleMatrix.setScale(1.0f, 1.0f);
        } else {
            this.scaleMatrix.setScale(((float) bounds.right) / mWidthDp, ((float) bounds.bottom) / mHeightDp);
        }
        this.perimeterPath.transform(this.scaleMatrix, this.scaledPerimeter);
        this.errorPerimeterPath.transform(this.scaleMatrix, this.scaledErrorPerimeter);
        this.fillMask.transform(this.scaleMatrix, this.scaledFill);
        this.scaledFill.computeBounds(this.fillRect, true);
        this.boltPath.transform(this.scaleMatrix, this.scaledBolt);
        this.plusPath.transform(this.scaleMatrix, this.scaledPlus);
        float max = Math.max(bounds.right / mWidthDp * 3f, 6f);
        getFillColorStrokePaint().setStrokeWidth(max);
        getFillColorStrokeProtection().setStrokeWidth(max);
        iconRect.set(bounds);
    }

    private final void loadPaths() {
        String pathString = this.context.getResources().getString(
                com.android.internal.R.string.config_batterymeterPerimeterPath);
        this.perimeterPath.set(PathParser.createPathFromPathData(pathString));
        this.perimeterPath.computeBounds(new RectF(), true);

        String errorPathString = this.context.getResources().getString(
                com.android.internal.R.string.config_batterymeterErrorPerimeterPath);
        this.errorPerimeterPath.set(PathParser.createPathFromPathData(errorPathString));
        this.errorPerimeterPath.computeBounds(new RectF(), true);

        String fillMaskString = this.context.getResources().getString(
                com.android.internal.R.string.config_batterymeterFillMask);
        this.fillMask.set(PathParser.createPathFromPathData(fillMaskString));
        this.fillMask.computeBounds(fillRect, true);

        String boltPathString = this.context.getResources().getString(
                com.android.internal.R.string.config_batterymeterBoltPath);
        this.boltPath.set(PathParser.createPathFromPathData(boltPathString));

        String plusPathString = this.context.getResources().getString(
                com.android.internal.R.string.config_batterymeterPowersavePath);
        this.plusPath.set(PathParser.createPathFromPathData(plusPathString));

        this.dualTone = this.context.getResources().getBoolean(
                com.android.internal.R.bool.config_batterymeterDualTone);
    }
}
