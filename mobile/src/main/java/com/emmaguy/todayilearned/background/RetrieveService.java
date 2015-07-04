package com.emmaguy.todayilearned.background;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.emmaguy.todayilearned.App;
import com.emmaguy.todayilearned.Logger;
import com.emmaguy.todayilearned.R;
import com.emmaguy.todayilearned.data.LatestPostsFromRedditRetriever;
import com.emmaguy.todayilearned.data.converter.MarkAsReadConverter;
import com.emmaguy.todayilearned.data.model.PostsDeserialiser;
import com.emmaguy.todayilearned.data.response.MarkAllReadResponse;
import com.emmaguy.todayilearned.data.retrofit.AuthenticatedRedditService;
import com.emmaguy.todayilearned.data.retrofit.RedditService;
import com.emmaguy.todayilearned.data.storage.TokenStorage;
import com.emmaguy.todayilearned.data.storage.UserStorage;
import com.emmaguy.todayilearned.sharedlib.Constants;
import com.emmaguy.todayilearned.sharedlib.Post;
import com.emmaguy.todayilearned.ui.DragReorderActionsPreference;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import retrofit.converter.GsonConverter;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class RetrieveService extends WakefulIntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String INTENT_KEY_INFORM_WATCH_NO_POSTS = "inform_no_posts";

    private GoogleApiClient mGoogleApiClient;

    @Inject AuthenticatedRedditService mAuthenticatedRedditService;
    @Inject LatestPostsFromRedditRetriever mLatestPostsRetriever;
    @Inject TokenStorage mTokenStorage;
    @Inject UserStorage mUserStorage;

    public RetrieveService() {
        super("RetrieveService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        App.with(this).getAppComponent().inject(this);
    }

    @Override
    protected void doWakefulWork(Intent intent) {
        connectToWearable();

        boolean informWatchIfNoPosts = false;
        if (intent.hasExtra(INTENT_KEY_INFORM_WATCH_NO_POSTS)) {
            informWatchIfNoPosts = intent.getBooleanExtra(INTENT_KEY_INFORM_WATCH_NO_POSTS, false);
        }

        retrieveLatestPostsFromReddit(informWatchIfNoPosts);
    }

    private void connectToWearable() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    private void retrieveLatestPostsFromReddit(final boolean sendInformationToWearableIfNoPosts) {
        final Gson gson = new GsonBuilder().registerTypeAdapter(Post.getPostsListTypeToken(), new PostsDeserialiser()).create();
        final RedditService redditService = mAuthenticatedRedditService.getRedditService(new GsonConverter(gson));
        mLatestPostsRetriever.getPosts(redditService)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Post>>() {
                    @Override
                    public void call(List<Post> posts) {
                        Logger.log("Found posts: " + posts.size());

                        if (posts.size() > 0) {
                            sendNewPostsData(posts);
                        } else if (sendInformationToWearableIfNoPosts) {
                            Logger.log("Sending no posts information");
                            WearListenerService.sendReplyResult(mGoogleApiClient, Constants.PATH_NO_NEW_POSTS);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Logger.sendThrowable(getApplicationContext(), "Failed to get latest posts", throwable);
                    }
                });

//        if (mTokenStorage.isLoggedIn() && mUserStorage.messagesEnabled()) {
//            redditService
//                    .unreadMessages()
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe(new Action1<List<Post>>() {
//                        @Override
//                        public void call(List<Post> messages) {
//                            Logger.log("Found messages: " + messages.size());
//
//                            if (messages.size() > 0) {
//                                sendNewPostsData(messages);
//
//                                mAuthenticatedRedditService.getRedditService(new MarkAsReadConverter())
//                                        .markAllMessagesRead()
//                                        .subscribeOn(Schedulers.io())
//                                        .observeOn(AndroidSchedulers.mainThread())
//                                        .subscribe(new Action1<MarkAllReadResponse>() {
//                                            @Override
//                                            public void call(MarkAllReadResponse markAllReadResponse) {
//                                                if (markAllReadResponse.hasErrors()) {
//                                                    throw new RuntimeException("Failed to mark all as read: " + markAllReadResponse);
//                                                }
//                                            }
//                                        }, new Action1<Throwable>() {
//                                            @Override
//                                            public void call(Throwable throwable) {
//                                                Logger.sendThrowable(getApplicationContext(), throwable.getMessage(), throwable);
//                                            }
//                                        });
//                            }
//                        }
//                    }, new Action1<Throwable>() {
//                        @Override
//                        public void call(Throwable throwable) {
//                            Logger.sendThrowable(getApplicationContext(), "Failed to get latest messages", throwable);
//                        }
//                    });
//        }
    }

    private void sendNewPostsData(List<Post> posts) {
        if (mGoogleApiClient.isConnected()) {
            Logger.log("sendNewPostsData: " + posts.size());

            Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
            final String latestPosts = gson.toJson(posts);

            // convert to json for sending to watch and to save to shared prefs
            // don't need to preserve the order like having separate String lists, can more easily add/remove fields
            PutDataMapRequest mapRequest = PutDataMapRequest.create(Constants.PATH_REDDIT_POSTS);
            DataMap dataMap = mapRequest.getDataMap();
            dataMap.putString(Constants.KEY_REDDIT_POSTS, latestPosts);

            for (Post p : posts) {
                if (p.hasThumbnail() || p.hasHighResImage()) {
                    Asset asset = Asset.createFromBytes(p.getImage());

                    Logger.log("Putting asset with id: " + p.getId() + " asset " + asset + " url: " + p.getThumbnail());
                    dataMap.putAsset(p.getId(), asset);
                }
            }
            ArrayList<Integer> actions = DragReorderActionsPreference.getSelectedActionsOrDefault(
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()),
                    getString(R.string.prefs_key_actions_order),
                    this);

            dataMap.putIntegerArrayList(Constants.KEY_ACTION_ORDER, actions);
            dataMap.putBoolean(Constants.KEY_DISMISS_AFTER_ACTION, mUserStorage.openOnPhoneDismissesAfterAction());
            dataMap.putLong("timestamp", System.currentTimeMillis());

            PutDataRequest request = mapRequest.asPutDataRequest();
            Logger.log("Sending request: " + request);
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            Logger.log("onResult: " + dataItemResult.getStatus());

                            if (dataItemResult.getStatus().isSuccess()) {
                                if (mGoogleApiClient.isConnected()) {
                                    mGoogleApiClient.disconnect();
                                }
                            }
                        }
                    });
        }
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public static Intent getFromWearableIntent(Context context) {
        final Intent intent = new Intent(context, RetrieveService.class);
        intent.putExtra(INTENT_KEY_INFORM_WATCH_NO_POSTS, true);
        return intent;
    }

    public static Intent getFromBackgroundSyncIntent(Context context) {
        return new Intent(context, RetrieveService.class);
    }
}
