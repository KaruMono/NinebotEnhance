package dev.ichinomiya.ninebotenhance.hook;

/** Capture replacement and narrowly scoped read-only observers, never vehicle command mutation. */
public final class HookPolicy {
    public static boolean captureClass(String name) {
        return name.startsWith("cn.ninebot.capture.") && !name.endsWith(".R") && !name.contains(".R$")
                && !name.endsWith("BuildConfig");
    }
    public static boolean interestingClass(String name) {
        return captureClass(name) || StatisticsHooks.interesting(name) || FeatureHooks.interesting(name) || name.equals("cn.ninebot.device.motor.thirdparts.TirePressureStateParser")
                || name.equals("cn.ninebot.device.DeviceManager") || name.equals("cn.ninebot.library.bluetooth.dynamic.DynamicDevice")
                || name.equals("cn.ninebot.mapcapture.DeviceScreenCastManager")
                || name.startsWith("cn.ninebot.mapcapture.DeviceScreenCastManager$")
                || name.startsWith("cn.ninebot.mapcapture.DeviceScreenCastRequest")
                || name.equals("cn.ninebot.mapcapture.NBBluetoothRtpSender")
                || name.equals("cn.ninebot.library.screencast.BluetoothRtpSender")
                || name.equals("cn.ninebot.device.motor.navi.DashNaviDataMessenger")
                || name.equals("cn.ninebot.device.motor.navi.DashNaviDataMessenger$Companion")
                || name.equals("cn.ninebot.device.motor.navi.CruiseModeActivity")
                || name.equals("cn.ninebot.device.motor.navi.CruiseModeActivity$Companion")
                || name.equals("cn.ninebot.device.motor.navi.ScreenCastHelper");
    }
    public static boolean vehiclePowerMethod(String className, String method) {
        return (className.equals("cn.ninebot.device.motor.navi.DashNaviDataMessenger$Companion")
                || className.equals("cn.ninebot.device.motor.navi.DashNaviDataMessenger")) && method.equals("isPowerOn");
    }
    public static boolean coroutineDrawingMethod(String className, String method) {
        return captureClass(className) && className.contains("$")
                && (method.equals("run") || method.equals("invoke") || method.equals("invokeSuspend") || method.equals("call"));
    }
    public static boolean bitmapGetter(String className, String method, int parameterCount, boolean bitmapReturn) {
        // No skipping coroutine bodies, timers or methods with transport/lifecycle side effects.
        // ViewEncoder and LoopBitmapEncoder compile their bitmap supplier into nested Kotlin
        // coroutine/state-machine classes. Those are the real per-frame entry points while
        // the phone display is asleep, so they must not be excluded by the '$' check.
        return captureClass(className) && method.equals("getBitmap") && parameterCount == 0 && bitmapReturn;
    }
    public static boolean terminalCastMethod(String className, String method) {
        return className.equals("cn.ninebot.mapcapture.DeviceScreenCastManager")
                && (method.equals("stop") || method.equals("stopScreenCast") || method.equals("stopCapture") || method.equals("release"));
    }
    private HookPolicy() {}
}
