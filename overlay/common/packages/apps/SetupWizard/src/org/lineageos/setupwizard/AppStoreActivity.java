/*
 * SPDX-FileCopyrightText: The NimbusOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppStoreActivity extends BaseSetupWizardActivity {

    private static final String INSTALL_ACTION =
            "org.lineageos.setupwizard.APP_STORE_INSTALL";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private CheckBox mInstallCheckbox;
    private TextView mStatus;
    private boolean mInstalling;

    private final BroadcastReceiver mInstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!INSTALL_ACTION.equals(intent.getAction())) {
                return;
            }
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                continueToNextPage();
            } else {
                showInstallError(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);

        mInstallCheckbox = findViewById(R.id.app_store_checkbox);
        mStatus = findViewById(R.id.app_store_status);
        View checkboxRow = findViewById(R.id.app_store_checkbox_view);
        checkboxRow.setOnClickListener(view -> mInstallCheckbox.setChecked(
                !mInstallCheckbox.isChecked()));

        registerReceiver(mInstallReceiver, new IntentFilter(INSTALL_ACTION));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mInstallReceiver);
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onNavigateNext() {
        if (!mInstallCheckbox.isChecked()) {
            continueToNextPage();
        } else if (!mInstalling) {
            downloadAndInstall();
        }
    }

    private void downloadAndInstall() {
        String apkUrl = getString(R.string.app_store_apk_url);
        if (!apkUrl.startsWith("https://")) {
            showInstallError(getString(R.string.app_store_invalid_url));
            return;
        }

        mInstalling = true;
        setNextAllowed(false);
        mStatus.setText(R.string.app_store_downloading);
        mExecutor.execute(() -> {
            PackageInstaller.Session session = null;
            try {
                PackageInstaller installer = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);
                try (OutputStream output = session.openWrite("base.apk", 0, -1)) {
                    download(apkUrl, output);
                    session.fsync(output);
                }
                Intent callback = new Intent(INSTALL_ACTION).setPackage(getPackageName());
                PendingIntent pendingIntent = PendingIntent.getBroadcast(this, sessionId,
                        callback, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                session.commit(pendingIntent.getIntentSender());
            } catch (Exception e) {
                Log.e(TAG, "Unable to download or install app store", e);
                if (session != null) {
                    session.abandon();
                }
                showInstallError(e.getMessage());
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        });
    }

    private static void download(String apkUrl, OutputStream output) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(apkUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void continueToNextPage() {
        if (!isFinishing()) {
            nextAction(RESULT_OK);
            finish();
        }
    }

    private void showInstallError(String message) {
        mHandler.post(() -> {
            mInstalling = false;
            setNextAllowed(true);
            mStatus.setText(getString(R.string.app_store_install_failed,
                    message == null ? "Unknown error" : message));
        });
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.app_store_page;
    }

    @Override
    protected int getTitleResId() {
        return R.string.app_store_title;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_features;
    }
}