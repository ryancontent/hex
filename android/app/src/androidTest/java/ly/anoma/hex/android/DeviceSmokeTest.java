package ly.anoma.hex.android;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.ParcelFileDescriptor;
import android.speech.RecognizerIntent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A real overlay and separate editor APK; recognition delivery is a test fixture. */
@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
    private Context context;
    private UiDevice device;
    private String previousIme, previousServices, previousAccessibility;
    private Preferences preferences;
    private static final String SERVICE = "ly.anoma.hex.android.poc/ly.anoma.hex.android.FloatingDictationService";
    private UiAutomation automation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }
    @Before public void setup() throws Exception {
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = new Preferences(context);
        preferences.setEnabled(false);
        preferences.resetBubblePosition();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        previousIme = shell("settings get secure default_input_method").trim();
        previousServices = shell("settings get secure enabled_accessibility_services").trim();
        previousAccessibility = shell("settings get secure accessibility_enabled").trim();
        automation().grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
        automation().grantRuntimePermission("ly.anoma.hex.android.poc.test", Manifest.permission.RECORD_AUDIO);
        InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, SetupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(device.wait(Until.hasObject(By.text("Hex for Android")), 10_000));
    }
    @After public void cleanup() throws Exception {
        preferences.setEnabled(false);
        shell("service call speech_recognition 2 i32 0");
        restore("enabled_accessibility_services", previousServices);
        restore("accessibility_enabled", previousAccessibility);
        assertEquals("Hex must never replace the keyboard", previousIme,
                shell("settings get secure default_input_method").trim());
        device.pressBack();
    }
    @Test public void labsIsOffUntilExplicitlyEnabled() {
        UiObject2 toggle = device.findObject(By.text("Experimental dictation"));
        assertNotNull(toggle);
        assertFalse(toggle.isChecked());
        toggle.click();
        assertTrue(device.wait(Until.hasObject(By.text("Experimental dictation").checked(true)), 5_000));
        assertTrue(preferences.enabled());
    }
    @Test public void tapTwiceInsertsAtCursorWithoutReplacingGboard() throws Exception {
        openExternalEditor();
        substituteRecognizer();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
        device.findObject(By.desc("Stop Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Hello lovely world")), 8_000));
        assertEquals(previousIme, shell("settings get secure default_input_method").trim());
    }
    @Test public void holdAndReleaseInserts() throws Exception {
        openExternalEditor();
        substituteRecognizer();
        android.graphics.Point point = device.findObject(By.desc("Start Hex dictation")).getVisibleCenter();
        device.swipe(point.x, point.y, point.x, point.y, 100);
        assertTrue(device.wait(Until.hasObject(By.text("Hello lovely world")), 8_000));
    }
    @Test public void automaticFinalWaitsUntilTheUserStops() throws Exception {
        openExternalEditor();
        shell("service call speech_recognition 2 i32 1 s16 ly.anoma.hex.android.poc.test "
                + "s16 ly.anoma.hex.android.EarlyResultRecognitionService");
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(waitForButtonState("Speech ready. Tap Stop or release to insert.", 5_000));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
        device.findObject(By.desc("Stop Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Hello lovely world")), 5_000));
    }
    @Test public void floatingDictationInsertsIntoHexPracticeField() throws Exception {
        enableOverlay();
        new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector().scrollable(true))
                .scrollIntoView(new androidx.test.uiautomator.UiSelector().description("Dictation practice field"));
        device.findObject(By.desc("Dictation practice field")).click();
        boolean shown = device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000);
        if (!shown) {
            java.io.File screenshot = new java.io.File(context.getExternalFilesDir(null), "field-failure.png");
            device.takeScreenshot(screenshot);
            shell("cp " + screenshot.getAbsolutePath() + " /data/local/tmp/hex-field-failure.png");
        }
        assertTrue(focusDescription(), shown);
        substituteRecognizer();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        device.findObject(By.desc("Stop Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.text("lovely")), 5_000));
        assertEquals("lovely", device.findObject(By.desc("Dictation practice field")).getText());
    }
    @Test public void changingFieldsCancelsAndPasswordHidesButton() throws Exception {
        openExternalEditor();
        substituteRecognizer();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        device.findObject(By.desc("External second field")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
        assertEquals("Keep this text", device.findObject(By.desc("External second field")).getText());
        device.findObject(By.desc("External password field")).click();
        assertTrue(device.wait(Until.gone(By.desc("Start Hex dictation")), 5_000));
    }
    @Test public void disablingLabsRemovesTheOverlay() throws Exception {
        openExternalEditor();
        preferences.setEnabled(false);
        assertTrue(device.wait(Until.gone(By.desc("Start Hex dictation")), 5_000));
    }
    @Test public void keyboardDismissalHidesBubbleEvenWhileTheEditorKeepsFocus() throws Exception {
        openExternalEditor();
        device.pressBack();
        assertTrue(device.wait(Until.gone(By.desc("Start Hex dictation")), 5_000));
        assertTrue(device.hasObject(By.desc("External first field")));
        device.findObject(By.desc("External first field")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        device.findObject(By.desc("Leave text fields")).click();
        assertTrue(device.wait(Until.gone(By.desc("Start Hex dictation")), 5_000));
    }
    @Test public void realOnDeviceServiceCanOpenEmulatorMicrophoneFromOverlay() throws Exception {
        org.junit.Assume.assumeTrue("Optional virtual-microphone proof needs a prepared emulator",
                InstrumentationRegistry.getArguments().getString("microphoneProof", "false").equals("true"));
        openExternalEditor();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(waitForButtonState("Listening… Speak, then tap Stop.", 10_000));
        var point = device.findObject(By.desc("Stop Hex dictation")).getVisibleCenter();
        device.swipe(point.x, point.y, point.x - Surface.dp(context, 100), point.y, 15);
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
    }
    @Test public void appHasNoInternetOrKeyboardServiceAndRequestsOfflineOnly() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                android.content.pm.PackageManager.GET_PERMISSIONS | android.content.pm.PackageManager.GET_SERVICES);
        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.RECORD_AUDIO));
        assertFalse(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.INTERNET));
        for (var service : info.services) assertNotEquals("android.permission.BIND_INPUT_METHOD", service.permission);
        assertTrue(SpeechSession.request("en-US").getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false));
    }
    @Test public void oneFixedButtonShowsIdleRecordingProcessingAndSuccess() throws Exception {
        openExternalEditor();
        substituteRecognizer("SlowFixtureRecognitionService");
        var idle = device.findObject(By.desc("Start Hex dictation"));
        android.graphics.Rect bounds = idle.getVisibleBounds();
        assertEquals("H", idle.getText());
        assertOneFloatingControl();
        saveButtonProof("idle");
        idle.click();
        assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        var recording = device.findObject(By.desc("Stop Hex dictation"));
        assertEquals(bounds, recording.getVisibleBounds());
        assertEquals("■", recording.getText());
        assertOneFloatingControl();
        saveButtonProof("recording");
        android.graphics.Bitmap firstFrame = automation().takeScreenshot();
        Thread.sleep(180);
        android.graphics.Bitmap nextFrame = automation().takeScreenshot();
        android.graphics.Bitmap firstScope = android.graphics.Bitmap.createBitmap(firstFrame,
                bounds.left, bounds.top, bounds.width(), bounds.height());
        android.graphics.Bitmap nextScope = android.graphics.Bitmap.createBitmap(nextFrame,
                bounds.left, bounds.top, bounds.width(), bounds.height());
        assertFalse("The listening scope must animate inside the same button", firstScope.sameAs(nextScope));
        firstScope.recycle(); nextScope.recycle(); firstFrame.recycle(); nextFrame.recycle();
        saveButtonProof("recording-next");
        recording.click();
        assertTrue(device.wait(Until.hasObject(By.desc("Hex is transcribing")), 5_000));
        var processing = device.findObject(By.desc("Hex is transcribing"));
        assertEquals(bounds, processing.getVisibleBounds());
        assertEquals("…", processing.getText());
        assertOneFloatingControl();
        saveButtonProof("processing");
        assertTrue(device.wait(Until.hasObject(By.text("Hello lovely world")), 8_000));
        assertEquals(bounds, device.findObject(By.desc("Start Hex dictation")).getVisibleBounds());
        assertOneFloatingControl();
    }
    @Test public void slidingOffTheSameButtonCancelsLockedRecording() throws Exception {
        openExternalEditor();
        substituteRecognizer();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        var point = device.findObject(By.desc("Stop Hex dictation")).getVisibleCenter();
        device.swipe(point.x, point.y, point.x - Surface.dp(context, 100), point.y, 15);
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
    }
    @Test public void slidingOffTheSameButtonCancelsProcessing() throws Exception {
        openExternalEditor();
        substituteRecognizer("SlowFixtureRecognitionService");
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        device.findObject(By.desc("Stop Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.desc("Hex is transcribing")), 5_000));
        var point = device.findObject(By.desc("Hex is transcribing")).getVisibleCenter();
        device.swipe(point.x, point.y, point.x - Surface.dp(context, 100), point.y, 15);
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
    }
    @Test public void hBubbleDragsToTheCenterAndRemembersPositionWithoutRecording() throws Exception {
        openExternalEditor();
        var first = device.findObject(By.desc("Start Hex dictation"));
        assertEquals("H", first.getText());
        var from = first.getVisibleCenter();
        int x = device.getDisplayWidth() / 2;
        int y = device.getDisplayHeight() / 3;
        device.swipe(from.x, from.y, x, y, 25);
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        var moved = device.findObject(By.desc("Start Hex dictation")).getVisibleCenter();
        assertTrue("Free x position, no side snapping", Math.abs(moved.x - x) < Surface.dp(context, 12));
        assertTrue("Free y position", Math.abs(moved.y - y) < Surface.dp(context, 12));
        assertTrue(preferences.hasBubblePosition());
        saveButtonProof("dragged");
        assertFalse(device.hasObject(By.desc("Stop Hex dictation")));
        assertEquals("Hello world", device.findObject(By.desc("External first field")).getText());
        preferences.setEnabled(false);
        assertTrue(device.wait(Until.gone(By.desc("Start Hex dictation")), 5_000));
        preferences.setEnabled(true);
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 5_000));
        assertEquals(moved, device.findObject(By.desc("Start Hex dictation")).getVisibleCenter());
        saveButtonProof("restored");
        assertOneFloatingControl();
        substituteRecognizer();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue("One H tap starts recording, no intermediate control", device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
        device.findObject(By.desc("Stop Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Hello lovely world")), 8_000));
    }
    private void saveButtonProof(String phase) throws Exception {
        if (!InstrumentationRegistry.getArguments().getString("captureButtonProof", "false").equals("true")) return;
        java.io.File file = new java.io.File(context.getExternalFilesDir(null), "single-button-" + phase + ".png");
        device.takeScreenshot(file);
        shell("cp " + file.getAbsolutePath() + " /data/local/tmp/single-button-" + phase + ".png");
    }
    private boolean waitForButtonState(String expected, long timeout) {
        long deadline = android.os.SystemClock.uptimeMillis() + timeout;
        do {
            for (var window : automation().getWindows()) {
                if (window.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
                var root = window.getRoot();
                if (root == null) continue;
                boolean matched = containsState(root, expected);
                root.recycle();
                if (matched) return true;
            }
            android.os.SystemClock.sleep(100);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        return false;
    }
    private boolean containsState(android.view.accessibility.AccessibilityNodeInfo node, String expected) {
        if (expected.contentEquals(node.getStateDescription() == null ? "" : node.getStateDescription())) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            var child = node.getChild(i);
            if (child == null) continue;
            boolean matched = containsState(child, expected);
            child.recycle();
            if (matched) return true;
        }
        return false;
    }
    private void assertOneFloatingControl() {
        int controls = 0;
        int windows = 0;
        for (var window : automation().getWindows()) {
            if (window.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue;
            var root = window.getRoot();
            if (root == null) continue;
            if (context.getPackageName().contentEquals(root.getPackageName())) {
                windows++;
                controls += countControls(root);
            }
            root.recycle();
        }
        assertEquals("Exactly one Hex overlay window", 1, windows);
        assertEquals("Exactly one clickable control", 1, controls);
        assertFalse(device.hasObject(By.desc("Cancel Hex dictation")));
        assertFalse(device.hasObject(By.desc("Hex dictation status")));
    }
    private int countControls(android.view.accessibility.AccessibilityNodeInfo node) {
        int result = node.isClickable() ? 1 : 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            var child = node.getChild(i);
            if (child == null) continue;
            result += countControls(child);
            child.recycle();
        }
        return result;
    }
    @Test public void realOnDevicePcmIsInsertedThroughProductionSessionOnStop() throws Exception {
        org.junit.Assume.assumeTrue("Requires a prepared offline language",
                InstrumentationRegistry.getArguments().getString("pipelineProof", "false").equals("true"));
        automation().adoptShellPermissionIdentity("android.permission.ACCESS_NETWORK_STATE");
        try { assertNull(context.getSystemService(android.net.ConnectivityManager.class).getActiveNetwork()); }
        finally { automation().dropShellPermissionIdentity(); }
        for (boolean hold : new boolean[]{false, true}) {
            openExternalEditor();
            shell("service call speech_recognition 2 i32 1 s16 ly.anoma.hex.android.poc.test "
                    + "s16 ly.anoma.hex.android.AudioFixtureRecognitionService");
            if (hold) {
                var point = device.findObject(By.desc("Start Hex dictation")).getVisibleCenter();
                device.swipe(point.x, point.y, point.x, point.y, 100);
            } else {
                device.findObject(By.desc("Start Hex dictation")).click();
                assertTrue(device.wait(Until.hasObject(By.desc("Stop Hex dictation")), 5_000));
                device.findObject(By.desc("Stop Hex dictation")).click();
            }
            var phrase = java.util.regex.Pattern.compile("(?is)Hello .*quick brown fox jumps over the lazy dog.* world");
            assertTrue(device.wait(Until.hasObject(By.desc("External first field").text(phrase)), 15_000));
            android.util.Log.i("HexPipelineProof", (hold ? "HOLD " : "TAP ")
                    + device.findObject(By.desc("External first field")).getText());
        }
    }
    private String focusDescription() {
        StringBuilder description = new StringBuilder();
        for (var window : automation().getWindows()) {
            description.append("window=").append(window.getType()).append(" active=").append(window.isActive());
            var root = window.getRoot();
            if (root == null) continue;
            var node = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (node != null) description.append(" editor=").append(node.getClassName())
                    .append(" id=").append(node.getViewIdResourceName()).append(" description=").append(node.getContentDescription())
                    .append(" editable=").append(node.isEditable()).append(" visible=").append(node.isVisibleToUser())
                    .append(" selection=").append(node.getTextSelectionStart()).append(",").append(node.getTextSelectionEnd());
            if (node != null) node.recycle();
            root.recycle();
        }
        return description.toString();
    }
    private void openExternalEditor() throws Exception {
        enableOverlay();
        context.startActivity(new Intent().setClassName("ly.anoma.hex.android.poc.test",
                "ly.anoma.hex.android.FixtureEditorActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(device.wait(Until.hasObject(By.desc("External first field")), 5_000));
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 10_000));
    }
    private void enableOverlay() throws Exception {
        preferences.setEnabled(true);
        String existing = previousServices.equals("null") || previousServices.isEmpty() ? "" : previousServices + ":";
        assertTrue(existing.matches("[A-Za-z0-9_.$/:]*"));
        // Instrumentation restarts the target process. If Hex was already enabled
        // manually, Android can retain its disconnected service as crashed; give
        // this test a fresh bind and restore the user's list during cleanup.
        String withoutHex = String.join(":", Arrays.stream(previousServices.split(":"))
                .filter(value -> !value.equals(SERVICE) && !value.equals("null")).toList());
        if (withoutHex.isEmpty()) shell("settings delete secure enabled_accessibility_services");
        else shell("settings put secure enabled_accessibility_services " + withoutHex);
        // UiAutomation executes argv directly, without a shell; quotes become literal
        // component-name characters. The validated value contains no whitespace.
        shell("settings put secure enabled_accessibility_services " + existing + SERVICE);
        shell("settings put secure accessibility_enabled 1");
    }
    private void substituteRecognizer() throws Exception {
        substituteRecognizer("FixtureRecognitionService");
    }
    private void substituteRecognizer(String service) throws Exception {
        String response = shell("service call speech_recognition 2 i32 1 s16 "
                + "ly.anoma.hex.android.poc.test s16 ly.anoma.hex.android." + service);
        assertFalse(response, response.contains("Exception"));
    }
    private void restore(String key, String value) throws Exception {
        assertTrue(value.matches("[A-Za-z0-9_.$/:]*"));
        if (value.equals("null") || value.isEmpty()) shell("settings delete secure " + key);
        else shell("settings put secure " + key + " " + value);
    }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation().executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
