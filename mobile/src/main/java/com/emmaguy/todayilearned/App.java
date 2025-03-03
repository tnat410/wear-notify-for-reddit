package com.emmaguy.todayilearned;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.emmaguy.todayilearned.storage.UniqueIdentifierStorage;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import javax.inject.Inject;
import javax.inject.Named;

import retrofit.RetrofitError;
import timber.log.Timber;

public class App extends Application {
    private final boolean mIsDebug = BuildConfig.DEBUG;

    private AppComponent mAppComponent;

    private static GoogleAnalytics sGoogleAnalytics;
    private static Tracker sTracker;

    @Inject @Named("analytics") UniqueIdentifierStorage mStorage;

    public static App with(Context context) {
        return (App) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mAppComponent = DaggerAppComponent.builder().appModule(new AppModule(this)).build();
        mAppComponent.inject(this);

        sGoogleAnalytics = GoogleAnalytics.getInstance(this);
        sGoogleAnalytics.setLocalDispatchPeriod(1800);

        sTracker = sGoogleAnalytics.newTracker(getString(R.string.google_analytics_id));
        sTracker.set("&uid", mStorage.getUniqueIdentifier());
        sTracker.enableExceptionReporting(true);
        sTracker.enableAdvertisingIdCollection(false);
        sTracker.enableAutoActivityTracking(true);

        if (mIsDebug) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new GoogleAnalyticsTree(sTracker));
        }
    }

    public AppComponent getAppComponent() {
        return mAppComponent;
    }

    public void sendEvent(String action, String label) {
        if (mIsDebug) {
            Timber.d("Sending event: " + action + " " + label);
        } else {
            sTracker.send(new HitBuilders.EventBuilder()
                    .setCategory("RedditWear" + BuildConfig.VERSION_NAME)
                    .setAction(action)
                    .setLabel(label)
                    .build());
        }
    }

    public boolean isDebug() {
        return mIsDebug;
    }

    private static class GoogleAnalyticsTree extends Timber.Tree {
        private final Tracker mTracker;

        private GoogleAnalyticsTree(Tracker tracker) {
            mTracker = tracker;
        }

        @Override protected void log(int priority, String tag, String message, Throwable t) {
            if (priority == Log.ERROR) {
                final String throwableMessage = message + ", msg: " + t.getMessage();
                final String stackTrace;
                if (t instanceof RetrofitError) {
                    final RetrofitError.Kind kind = ((RetrofitError) t).getKind();
                    if (kind == RetrofitError.Kind.NETWORK) {
                        stackTrace = "Network";
                    } else {
                        stackTrace = Log.getStackTraceString(t);
                    }
                } else {
                    stackTrace = Log.getStackTraceString(t);
                }

                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("RedditWear" + BuildConfig.VERSION_NAME)
                        .setAction(stackTrace)
                        .setLabel(throwableMessage)
                        .build());
            } else {
                mTracker.send(new HitBuilders.EventBuilder()
                        .setCategory("Debug_RedditWear" + BuildConfig.VERSION_NAME)
                        .setAction(message)
                        .build());
            }
        }
    }
}
