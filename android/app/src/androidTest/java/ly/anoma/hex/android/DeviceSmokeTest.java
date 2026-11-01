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
    @Test public void realOnDeviceServiceCanOpenEmulatorMicrophoneFromOverlay() throws Exception {
        org.junit.Assume.assumeTrue("Optional virtual-microphone proof needs a prepared emulator",
                InstrumentationRegistry.getArguments().getString("microphoneProof", "false").equals("true"));
        openExternalEditor();
        device.findObject(By.desc("Start Hex dictation")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Listening… Speak, then tap Stop.")), 10_000));
        device.findObject(By.desc("Cancel Hex dictation")).click();
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
    private void openExternalEditor() throws Exception {
        preferences.setEnabled(true);
        String existing = previousServices.equals("null") || previousServices.isEmpty() ? "" : previousServices + ":";
        assertTrue(existing.matches("[A-Za-z0-9_.$/:]*"));
        // UiAutomation executes argv directly, without a shell; quotes become literal
        // component-name characters. The validated value contains no whitespace.
        shell("settings put secure enabled_accessibility_services " + existing + SERVICE);
        shell("settings put secure accessibility_enabled 1");
        context.startActivity(new Intent().setClassName("ly.anoma.hex.android.poc.test",
                "ly.anoma.hex.android.FixtureEditorActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(device.wait(Until.hasObject(By.desc("External first field")), 5_000));
        assertTrue(device.wait(Until.hasObject(By.desc("Start Hex dictation")), 10_000));
    }
    private void substituteRecognizer() throws Exception {
        String response = shell("service call speech_recognition 2 i32 1 s16 "
                + "ly.anoma.hex.android.poc.test s16 ly.anoma.hex.android.FixtureRecognitionService");
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
