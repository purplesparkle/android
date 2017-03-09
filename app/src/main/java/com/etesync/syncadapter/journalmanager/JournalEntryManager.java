package com.etesync.syncadapter.journalmanager;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import com.etesync.syncadapter.App;
import com.etesync.syncadapter.GsonHelper;

import lombok.Getter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class JournalEntryManager extends BaseManager {
    @Getter
    final String uid;
    final static private Type entryType = new TypeToken<List<Entry>>() {
    }.getType();


    public JournalEntryManager(OkHttpClient httpClient, HttpUrl remote, String journal) {
        this.uid = journal;
        this.remote = remote.newBuilder()
                .addPathSegments("api/v1/journal")
                .addPathSegments(journal)
                .addPathSegment("")
                .build();
        App.log.info("Created for: " + this.remote.toString());

        this.client = httpClient;
    }

    public List<Entry> getEntries(String keyBase64, String last) throws Exceptions.HttpException, Exceptions.IntegrityException {
        Entry previousEntry = null;
        HttpUrl.Builder urlBuilder = this.remote.newBuilder();
        if (last != null) {
            urlBuilder.addQueryParameter("last", last);
            previousEntry = Entry.getFakeWithUid(last);
        }

        HttpUrl remote = urlBuilder.build();

        Request request = new Request.Builder()
                .get()
                .url(remote)
                .build();

        Response response = newCall(request);
        ResponseBody body = response.body();
        List<Entry> ret = GsonHelper.gson.fromJson(body.charStream(), entryType);

        for (Entry entry : ret) {
            entry.verify(keyBase64, previousEntry);
            previousEntry = entry;
        }

        return ret;
    }

    public void putEntries(List<Entry> entries, String last) throws Exceptions.HttpException {
        HttpUrl.Builder urlBuilder = this.remote.newBuilder();
        if (last != null) {
            urlBuilder.addQueryParameter("last", last);
        }

        HttpUrl remote = urlBuilder.build();

        RequestBody body = RequestBody.create(JSON, GsonHelper.gson.toJson(entries, entryType));

        Request request = new Request.Builder()
                .post(body)
                .url(remote)
                .build();

        newCall(request);
    }

    public static class Entry extends Base {
        public Entry() {
            super();
        }

        public void update(String keyBase64, String content, Entry previous) {
            setContent(keyBase64, content);
            setUid(calculateHmac(keyBase64, previous));
        }

        void verify(String keyBase64, Entry previous) throws Exceptions.IntegrityException {
            String correctHash = calculateHmac(keyBase64, previous);
            if (!getUuid().equals(correctHash)) {
                throw new Exceptions.IntegrityException("Bad HMAC. " + getUuid() + " != " + correctHash);
            }
        }

        public static Entry getFakeWithUid(String uid) {
            Entry ret = new Entry();
            ret.setUid(uid);
            return ret;
        }

        private String calculateHmac(String keyBase64, Entry previous) {
            String uuid = null;
            if (previous != null) {
                uuid = previous.getUuid();
            }

            return Helpers.toHex(calculateHmac(keyBase64, uuid));
        }
    }

}
