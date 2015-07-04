package com.emmaguy.todayilearned.data.auth;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;

import com.emmaguy.todayilearned.R;
import com.emmaguy.todayilearned.data.storage.UniqueIdentifierStorage;
import com.emmaguy.todayilearned.sharedlib.Constants;

import java.util.UUID;

/**
 * Created by emma on 14/06/15.
 */
public class RedditAccessTokenRequester {
    private final Context mContext;
    private final Resources mResources;
    private final UniqueIdentifierStorage mUniqueIdentifierStorage;

    public RedditAccessTokenRequester(Context context, Resources resources, UniqueIdentifierStorage uniqueIdentifierStorage) {
        mContext = context;
        mResources = resources;
        mUniqueIdentifierStorage = uniqueIdentifierStorage;
    }

    public void request() {
        String randomIdentifier = UUID.randomUUID().toString();
        mUniqueIdentifierStorage.store(randomIdentifier);

        final String redirectUrl = mResources.getString(R.string.redirect_url_scheme) + mResources.getString(R.string.redirect_url_callback);

        String url = Constants.ENDPOINT_URL_REDDIT + "api/v1/authorize?" +
                "client_id=" + mResources.getString(R.string.client_id) +
                "&duration=" + mResources.getString(R.string.reddit_auth_duration) +
                "&response_type=code" +
                "&state=" + randomIdentifier +
                "&redirect_uri=" + redirectUrl +
                "&scope=" + mResources.getString(R.string.reddit_auth_scope);

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(browserIntent);
    }
}
