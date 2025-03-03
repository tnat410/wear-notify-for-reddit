package com.emmaguy.todayilearned.storage;

public interface UserStorage {
    int getNumberToRequest();

    void setSeenTimestamp(long timestamp);
    void clearTimestamp();

    long getTimestamp();
    String getSortType();
    String getSubreddits();
    String getRefreshInterval();

    boolean messagesEnabled();
    boolean downloadFullSizedImages();
    boolean openOnPhoneDismissesAfterAction();
}
