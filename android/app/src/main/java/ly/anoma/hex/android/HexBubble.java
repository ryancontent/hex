package ly.anoma.hex.android;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;

/** Canvas adaptation of desktop Dot scope, inside the original single touch target. */
final class HexBubble extends Button {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mark = new Path();
    private boolean listening, processing, feedback, error;
    private float level;
    private long levelAt, previousFrame;
    private float envelope;

    HexBubble(Context context) {
        super(context);
        setBackground(null);
        setPadding(0, 0, 0, 0);
        setTextColor(Color.TRANSPARENT); // Keep semantic state text available to Accessibility.
    }

    void phase(boolean listening, boolean processing, boolean feedback, boolean error) {
        this.listening = listening;
        this.processing = processing;
        this.feedback = feedback;
        this.error = error;
        if (!listening) { level = 0; envelope = 0; }
        invalidate();
    }

    void level(float rms) {
        level = Float.isFinite(rms) ? Math.max(0, Math.min(1, (rms + 2) / 12)) : 0;
        levelAt = SystemClock.uptimeMillis();
    }

    @Override protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
        canvas.scale(size / 60, size / 60);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(17, 17, 20));
        canvas.drawCircle(30, 30, 28.5f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f);
        paint.setColor(Color.argb(40, 255, 255, 255));
        canvas.drawCircle(30, 30, 28.1f, paint);
        paint.setStyle(Paint.Style.FILL);
        long now = SystemClock.uptimeMillis();
        boolean motion = ValueAnimator.areAnimatorsEnabled();
        float time = motion ? now / 1000f : 0;
        float dt = previousFrame == 0 ? 0.016f : Math.min(0.1f, (now - previousFrame) / 1000f);
        previousFrame = now;
        float target = now - levelAt < 250 ? level : 0;
        envelope += (target - envelope) * (1 - (float)Math.exp(-dt * (target > envelope ? 14 : 5)));
        if (listening || processing) drawScope(canvas, time, motion);
        else if (feedback) drawFeedback(canvas);
        else drawMark(canvas);
        canvas.restore();
        if ((listening || processing) && motion && isAttachedToWindow() && getWindowVisibility() == View.VISIBLE)
            postInvalidateOnAnimation();
    }

    private void drawMark(Canvas canvas) {
        paint.setColor(Surface.INK);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(1.75f);
        paint.setStyle(Paint.Style.STROKE);
        hex(30, 30, 15.1f); canvas.drawPath(mark, paint);
        hex(30, 30, 9.65f); canvas.drawPath(mark, paint);
        paint.setStyle(Paint.Style.FILL);
        hex(30, 30, 4.8f); canvas.drawPath(mark, paint);
    }
    private void hex(float x, float y, float radius) {
        mark.reset();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3 * i - Math.PI / 2;
            float px = x + radius * (float)Math.cos(angle), py = y + radius * (float)Math.sin(angle);
            if (i == 0) mark.moveTo(px, py); else mark.lineTo(px, py);
        }
        mark.close();
    }
    private void drawScope(Canvas canvas, float time, boolean motion) {
        // Desktop palette: warm pearl for the speaker; cool agent blue for processing.
        int red = processing ? 179 : 219, green = processing ? 199 : 212, blue = processing ? 230 : 196;
        for (int column = 0; column < 13; column++) {
            float x = 12 + column * 3;
            float u = column / 12f;
            float amplitude = motion ? envelope * 7 : 3;
            float wave = 30 + amplitude * (float)Math.sin(u * Math.PI * 3 - time * 3);
            float braid = 30 - amplitude * (float)Math.sin(u * Math.PI * 3 - time * 3 + 0.8);
            for (int row = 0; row < 7; row++) {
                float y = 21 + row * 3;
                float distance = Math.min(Math.abs(y - wave), Math.abs(y - braid));
                float power = (float)Math.exp(-distance * distance / 5);
                if (processing) power *= 0.35f + 0.65f * (0.5f + 0.5f * (float)Math.sin(u * 7 - time * 4));
                paint.setColor(Color.argb(Math.round(22 + power * 230), red, green, blue));
                canvas.drawCircle(x, y, 0.75f + power * 0.25f, paint);
            }
        }
    }
    private void drawFeedback(Canvas canvas) {
        paint.setColor(error ? Color.rgb(255, 97, 79) : Surface.INK);
        paint.setStrokeWidth(2);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (error) {
            canvas.drawLine(30, 21, 30, 31, paint);
            canvas.drawCircle(30, 37, 1.2f, paint);
        } else {
            mark.reset(); mark.moveTo(22, 30); mark.lineTo(28, 36); mark.lineTo(39, 24);
            paint.setStyle(Paint.Style.STROKE); canvas.drawPath(mark, paint); paint.setStyle(Paint.Style.FILL);
        }
    }
}
