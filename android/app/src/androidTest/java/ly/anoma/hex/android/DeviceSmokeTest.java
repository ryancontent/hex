package ly.anoma.hex.android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.ParcelFileDescriptor;
import android.speech.RecognizerIntent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
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

/** These tests exercise setup and the real IME, not model accuracy or microphone hardware. */
@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
    private Context context;
    private UiDevice device;
    private String previousIme;
    private Preferences preferences;
    private SetupActivity activity;

    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = new Preferences(context);
        preferences.setEnabled(false);
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        previousIme = shell("settings get secure default_input_method").trim();
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
        automation.grantRuntimePermission("ly.anoma.hex.android.poc.test", Manifest.permission.RECORD_AUDIO);
        shell("ime enable " + context.getPackageName() + "/ly.anoma.hex.android.HexKeyboard");
        shell("ime set " + context.getPackageName() + "/ly.anoma.hex.android.HexKeyboard");
        activity = (SetupActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, SetupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(device.wait(Until.hasObject(By.text("Hex for Android")), 10_000));
    }

    @After public void cleanup() throws Exception {
        preferences.setEnabled(false);
        device.pressBack();
        if (!previousIme.isEmpty() && !previousIme.equals("null")) shell("ime set " + previousIme);
        shell("ime disable " + context.getPackageName() + "/ly.anoma.hex.android.HexKeyboard");
    }

    @Test public void labsIsOffUntilExplicitlyEnabled() {
        UiObject2 toggle = device.findObject(By.text("Experimental dictation"));
        assertNotNull(toggle);
        assertFalse(toggle.isChecked());
        toggle.click();
        assertTrue(device.wait(Until.hasObject(By.text("Experimental dictation").checked(true)), 5_000));
        assertTrue(preferences.enabled());
    }

    @Test public void keyboardStartsAndEnforcesLabsGate() {
        focusPracticeField();
        device.findObject(By.text("Dictate")).click();
        assertTrue(device.wait(Until.hasObject(By.text("Turn on dictation in Hex’s Labs first.")), 5_000));
        assertFalse(preferences.enabled());
        assertTrue(device.hasObject(By.text("Dictate")));
    }

    @Test public void keyboardInsertsOnlyFinalTextThroughRealInputConnection() throws Exception {
        org.junit.Assume.assumeTrue(android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context));
        preferences.setEnabled(true);
        // This is Android's temporary test-service substitution, scoped to 60 seconds
        // and reset in finally. It does not change the factory used by production.
        String response = shell("service call speech_recognition 2 i32 1 s16 "
                + "ly.anoma.hex.android.poc.test s16 ly.anoma.hex.android.FixtureRecognitionService");
        assertFalse(response, response.contains("Exception"));
        try {
            focusPracticeField();
            device.findObject(By.text("Dictate")).click();
            assertTrue(device.wait(Until.hasObject(By.textContains("Inserted · on-device")), 10_000));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                android.widget.EditText field = activity.findViewById(R.id.practice);
                assertEquals("Hex keyboard works.", field.getText().toString());
            });
            assertTrue(device.hasObject(By.text("Dictate")));
        } finally { shell("service call speech_recognition 2 i32 0"); }
    }

    private void focusPracticeField() {
        UiObject2 field = device.findObject(By.desc("Dictation practice field"));
        // The form may extend below the viewport; scroll to bring the field into view.
        for (int i = 0; field == null && i < 5; i++) {
            device.swipe(device.getDisplayWidth() / 2, device.getDisplayHeight() * 3 / 4,
                    device.getDisplayWidth() / 2, device.getDisplayHeight() / 3, 20);
            device.waitForIdle();
            field = device.findObject(By.desc("Dictation practice field"));
        }
        assertNotNull(field);
        field.click();
        assertTrue(device.wait(Until.hasObject(By.text("Hex  /  Labs")), 5_000));
    }

    @Test public void appHasNoInternetPermissionAndRequestsOfflineOnly() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        assertTrue(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.RECORD_AUDIO));
        assertFalse(Arrays.asList(info.requestedPermissions).contains(Manifest.permission.INTERNET));
        Intent request = SpeechSession.request("en-US");
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false));
        assertEquals("en-US", request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
