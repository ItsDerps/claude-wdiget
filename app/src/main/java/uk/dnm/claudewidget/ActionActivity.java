package uk.dnm.claudewidget;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;

/** Invisible activity that does whatever the tapped widget button asks for. */
public class ActionActivity extends Activity {

    static final String CHAT = "uk.dnm.claudewidget.CHAT";
    static final String CAMERA = "uk.dnm.claudewidget.CAMERA";
    static final String VOICE = "uk.dnm.claudewidget.VOICE";
    static final String CLAUDE = "com.anthropic.claude";
    private static final int REQ_PHOTO = 7;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) return; // returning from the camera after a rotation etc.
        String a = getIntent().getAction();
        if (CAMERA.equals(a)) {
            takePhoto();
        } else if (VOICE.equals(a)) {
            openVoice();
            finish();
        } else {
            openClaude();
            finish();
        }
    }

    // ---- Chat ----
    private void openClaude() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(CLAUDE);
        if (launch == null) {
            Toast.makeText(this, "Claude app isn't installed", Toast.LENGTH_SHORT).show();
            startSafe(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + CLAUDE)));
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startSafe(launch);
    }

    // ---- Voice: try Claude's assistant entry points, else just open Claude ----
    private void openVoice() {
        String[] actions = {Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST};
        for (String act : actions) {
            Intent i = new Intent(act).setPackage(CLAUDE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(getPackageManager()) != null && startSafe(i)) return;
        }
        openClaude();
    }

    // ---- Camera: take a photo, then hand it straight to Claude ----
    private Uri photoUri() {
        return Uri.parse("content://uk.dnm.claudewidget.photo/photo.jpg");
    }

    private void takePhoto() {
        new File(getCacheDir(), "photo.jpg").delete();
        Uri out = photoUri();
        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, out)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        cam.setClipData(ClipData.newRawUri("photo", out));
        try {
            startActivityForResult(cam, REQ_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PHOTO && res == RESULT_OK
                && new File(getCacheDir(), "photo.jpg").length() > 0) {
            Uri uri = photoUri();
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            send.setClipData(ClipData.newRawUri("photo", uri));
            Intent toClaude = new Intent(send).setPackage(CLAUDE);
            if (toClaude.resolveActivity(getPackageManager()) == null || !startSafe(toClaude)) {
                startSafe(Intent.createChooser(send, "Send to"));
            }
        }
        finish();
    }

    private boolean startSafe(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
