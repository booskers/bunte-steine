package cc.polychrome.buntesteine;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Keeps the game up to date from its GitHub releases (booskers/bunte-steine). Every release carries bunte-steine.json
 * ({"versionCode": 3, "versionName": "1.2.0"}) next to Bunte-Steine.apk.
 *
 * Nobody playing is ever asked anything or interrupted: the game looks for a newer version when it is left and every
 * hour in the background, and installs it only while the game is not on the screen, quietly. Next time it is opened,
 * it simply is the new version (the saved game stays). Android allows that from Android 12 on once the game is its
 * own installer: "Automatische Updates einschalten" in the settings reinstalls the game's own copy once (one tap by a
 * family member) so that it is. Otherwise an update waits in the settings. Android only accepts an update signed with
 * the same key, so nothing else can be slipped in.
 */
public class Updater {
    static final String BASE = "https://github.com/booskers/bunte-steine/releases/latest/download/";
    static final String INFO = BASE + "bunte-steine.json", APK = BASE + "Bunte-Steine.apk";
    static final String DONE = "cc.polychrome.buntesteine.INSTALLED";
    static final long EVERY = 60 * 60 * 1000L;   // every hour at most
    static final int JOB = 4711;

    /** the game is on the screen: nothing is installed now (it would close the game) */
    static volatile boolean visible;
    static volatile boolean busy;
    static Runnable onChange;   // the open game's page is told when something changed

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("updates", Context.MODE_PRIVATE); }
    static void changed() { Runnable r = onChange; if (r != null) r.run(); }
    static void status(Context c, String s) { prefs(c).edit().putString("status", s).apply(); changed(); }

    @SuppressWarnings("deprecation")
    static long myVersion(Context c) {
        try {
            android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return Long.MAX_VALUE; }
    }

    static String myName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    static boolean available(Context c) { return prefs(c).getLong("newCode", -1) > myVersion(c); }
    static boolean canInstall(Context c) { return c.getPackageManager().canRequestPackageInstalls(); }

    /** true when Android counts the game as its own installer, so updates may go in without anyone asked */
    static boolean ownInstaller(Context c) {
        if (Build.VERSION.SDK_INT < 31) return false;
        try { return c.getPackageName().equals(c.getPackageManager().getInstallSourceInfo(c.getPackageName()).getInstallingPackageName()); }
        catch (Exception e) { return false; }
    }

    /** for the settings window */
    static String info(Context c) {
        SharedPreferences p = prefs(c);
        try {
            return new JSONObject().put("current", myName(c)).put("available", available(c)).put("name", p.getString("newName", ""))
                .put("status", busy ? "downloading" : p.getString("status", "")).put("canInstall", canInstall(c))
                .put("quiet", Build.VERSION.SDK_INT >= 31).put("auto", ownInstaller(c)).toString();
        } catch (Exception e) { return "{}"; }
    }

    /** the hourly look in the background, kept across restarts of the phone */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB) != null) return;
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, Job.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(EVERY).setPersisted(true).build());
    }

    /** look for a newer version (at most hourly unless `byHand`), then install it quietly if the game isn't showing */
    static void check(Context c, boolean byHand, Runnable done) {
        Context a = c.getApplicationContext();
        if (busy) { if (done != null) done.run(); return; }
        if (!byHand && System.currentTimeMillis() - prefs(a).getLong("checked", 0) < EVERY) {
            if (available(a)) install(a, false);
            if (done != null) done.run();
            return;
        }
        busy = true;
        if (byHand) status(a, "checking");
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject(new String(fetch(INFO + "?t=" + System.currentTimeMillis()), "UTF-8"));
                prefs(a).edit().putLong("checked", System.currentTimeMillis()).putLong("newCode", j.getLong("versionCode"))
                    .putString("newName", j.optString("versionName", "")).putString("status", "").apply();
                busy = false;
                changed();
                if (available(a) && !byHand) install(a, false);
            } catch (Exception e) {   // offline, or GitHub unreachable: quietly try again next time
                busy = false;
                status(a, byHand ? "offline" : "");
            }
            if (done != null) done.run();
        }).start();
    }

    /** `byHand`: from the settings, where Android's confirmation may be shown. Otherwise only quietly, never while playing. */
    static void install(Context c, boolean byHand) {
        Context a = c.getApplicationContext();
        if (busy || !available(a)) return;
        if (!canInstall(a)) {
            if (byHand) c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            else status(a, "needsOk");
            return;
        }
        if (!byHand && (visible || !ownInstaller(a))) { if (!ownInstaller(a)) status(a, "needsOk"); return; }
        busy = true;
        changed();
        new Thread(() -> {
            try {
                HttpURLConnection h = open(APK);
                try (InputStream in = h.getInputStream()) { session(a, in, h.getContentLengthLong(), byHand); }
            } catch (Exception e) { busy = false; status(a, byHand ? "failed" : ""); }
        }).start();
    }

    /** "Automatische Updates einschalten": install the game over itself once (Android asks one time), which makes it
     *  its own installer; from then on updates need nobody */
    static void becomeInstaller(Context c) {
        Context a = c.getApplicationContext();
        if (busy) return;
        if (!canInstall(a)) { c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); return; }
        busy = true;
        changed();
        new Thread(() -> {
            File me = new File(a.getApplicationInfo().sourceDir);
            try (InputStream in = new FileInputStream(me)) { session(a, in, me.length(), true); }
            catch (Exception e) { busy = false; status(a, "failed"); }
        }).start();
    }

    static void session(Context a, InputStream in, long total, boolean byHand) throws Exception {
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(a.getPackageName());
            if (Build.VERSION.SDK_INT >= 31) sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            if (total > 0) sp.setSize(total);
            id = pi.createSession(sp);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (OutputStream out = s.openWrite("update.apk", 0, total)) {
                    byte[] buf = new byte[64 * 1024]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent done = new Intent(a, Done.class).setAction(DONE).putExtra("byHand", byHand);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                s.commit(PendingIntent.getBroadcast(a, 41, done, flags).getIntentSender());
            }
        } catch (Exception e) {
            if (id >= 0) try { pi.abandonSession(id); } catch (Exception ignored) { }
            throw e;
        }
    }

    /** what the installer says: it needs a person's OK (only shown when asked for in the settings), it worked, or not */
    public static class Done extends BroadcastReceiver {
        @Override
        @SuppressWarnings("deprecation")
        public void onReceive(Context c, Intent in) {
            busy = false;
            int st = in.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = in.getParcelableExtra(Intent.EXTRA_INTENT);
                if (in.getBooleanExtra("byHand", false) && confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); status(c, ""); } catch (Exception e) { status(c, "needsOk"); }
                } else status(c, "needsOk");
            } else if (st == PackageInstaller.STATUS_SUCCESS) status(c, "");
            else status(c, st == PackageInstaller.STATUS_FAILURE_ABORTED ? "" : "failed");
        }
    }

    /** the hourly background look */
    public static class Job extends JobService {
        @Override public boolean onStartJob(JobParameters p) { check(this, false, () -> jobFinished(p, false)); return true; }
        @Override public boolean onStopJob(JobParameters p) { return true; }
    }

    static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);   // GitHub sends release files on to its download host (https to https)
        c.setRequestProperty("User-Agent", "BunteSteine");
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return c;
    }

    static byte[] fetch(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0 && b.size() < 64 * 1024) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }
}
