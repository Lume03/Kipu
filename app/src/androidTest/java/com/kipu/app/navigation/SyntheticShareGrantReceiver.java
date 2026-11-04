package com.kipu.app.navigation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Platform-only code: test APK components run separately without the target's dependencies. */
public final class SyntheticShareGrantReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Uri uri = Uri.parse("content://" + context.getPackageName() + ".secure-share-fixture/voucher.png");
        if ("fixture.REVOKE".equals(intent.getAction())) {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            new java.io.File(context.getCacheDir(), "synthetic-yape-fixture.png").delete();
        } else if ("fixture.GRANT".equals(intent.getAction())) {
            context.grantUriPermission("com.kipu.app", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        setResultData(uri.toString());
    }
}
