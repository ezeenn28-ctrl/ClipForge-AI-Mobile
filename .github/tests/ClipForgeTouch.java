import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Isolated emulator automation only; never packaged in the owner APK. */
public final class ClipForgeTouch {
    public static void main(String[] args) throws Exception {
        float x = Float.parseFloat(args[0]), y = Float.parseFloat(args[1]);
        Class<?> type = Class.forName("android.hardware.input.InputManager");
        Object manager = type.getMethod("getInstance").invoke(null);
        Method inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 0;
        property.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinate = new MotionEvent.PointerCoords();
        coordinate.x = x; coordinate.y = y; coordinate.pressure = 1; coordinate.size = 1;
        MotionEvent.PointerProperties[] properties = {property};
        MotionEvent.PointerCoords[] coordinates = {coordinate};
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            if (action == MotionEvent.ACTION_UP) { Thread.sleep(70); coordinate.pressure = 0; }
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                properties, coordinates, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            try {
                if (!Boolean.TRUE.equals(inject.invoke(manager, event, 2)))
                    throw new AssertionError("Typed finger event was not injected");
            } finally { event.recycle(); }
        }
    }
}
