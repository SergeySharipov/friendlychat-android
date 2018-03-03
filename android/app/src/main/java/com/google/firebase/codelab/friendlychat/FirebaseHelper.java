package com.google.firebase.codelab.friendlychat;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.codelab.friendlychat.model.FriendlyMessage;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;

import static com.google.firebase.codelab.friendlychat.Constants.FRIENDLY_MSG_LENGTH;
import static com.google.firebase.codelab.friendlychat.Constants.LOADING_IMAGE_URL;
import static com.google.firebase.codelab.friendlychat.Constants.MESSAGES_CHILD;

public class FirebaseHelper {
    public static final String ANONYMOUS = "anonymous";
    private static final String TAG = "FirebaseHelper";
    private DatabaseReference mFirebaseDatabaseReference;
    private DatabaseReference mMessagesRef;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private SnapshotParser<FriendlyMessage> mFriendlyMessageSnapshotParser;
    private FirebaseRecyclerOptions<FriendlyMessage> mFriendlyMsgFirebaseRecyclerOptions;

    private GoogleApiClient mGoogleApiClient;

    private String mUsername;
    private String mPhotoUrl;

    private OnChangeConfigListener mOnChangeConfigListener;

    public FirebaseHelper(GoogleApiClient googleApiClient, OnChangeConfigListener onChangeConfigListener) {
        mGoogleApiClient = googleApiClient;
        mOnChangeConfigListener = onChangeConfigListener;

        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mMessagesRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD);

        // Initialize Firebase Auth
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseUser = mFirebaseAuth.getCurrentUser();

        mUsername = ANONYMOUS;

        if (isUserAuthorized()) {
            mUsername = mFirebaseUser.getDisplayName();
            if (mFirebaseUser.getPhotoUrl() != null) {
                mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
            }
            Log.e(TAG, "mFirebaseUser " + mUsername);
        }

        mFriendlyMessageSnapshotParser = new SnapshotParser<FriendlyMessage>() {
            @Override
            public FriendlyMessage parseSnapshot(DataSnapshot dataSnapshot) {
                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                if (friendlyMessage != null) {
                    friendlyMessage.setId(dataSnapshot.getKey());
                }
                return friendlyMessage;
            }
        };

        mFriendlyMsgFirebaseRecyclerOptions =
                new FirebaseRecyclerOptions.Builder<FriendlyMessage>()
                        .setQuery(mMessagesRef, mFriendlyMessageSnapshotParser)
                        .build();

        setupConfig();
    }

    private void setupConfig() {
        // Initialize Firebase Remote Config.
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        // Define Firebase Remote Config Settings.
        FirebaseRemoteConfigSettings firebaseRemoteConfigSettings =
                new FirebaseRemoteConfigSettings.Builder()
                        .setDeveloperModeEnabled(true)
                        .build();

        // Define default config values. Defaults are used when fetched config values are not
        // available. Eg: if an error occurred fetching values from the server.
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put("friendly_msg_length", 10L);

        // Apply config settings and default values.
        mFirebaseRemoteConfig.setConfigSettings(firebaseRemoteConfigSettings);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
    }

    public FirebaseRecyclerOptions<FriendlyMessage> getFriendlyMsgFirebaseRecyclerOptions() {
        return mFriendlyMsgFirebaseRecyclerOptions;
    }

    public boolean isUserAuthorized() {
        return mFirebaseUser != null;
    }

    public DatabaseReference getMessagesRef() {
        return mMessagesRef;
    }

    public void singOut() {
        mFirebaseAuth.signOut();
        Auth.GoogleSignInApi.signOut(mGoogleApiClient);
        mFirebaseUser = null;
        mUsername = ANONYMOUS;
        mPhotoUrl = null;
    }

    public void sendImage(final Uri imageUri) {
        if (imageUri != null) {
            FriendlyMessage tempMessage = new FriendlyMessage(null, mUsername, mPhotoUrl,
                    LOADING_IMAGE_URL);//todo
            mFirebaseDatabaseReference.child(MESSAGES_CHILD).push()
                    .setValue(tempMessage, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(DatabaseError databaseError,
                                               DatabaseReference databaseReference) {
                            if (databaseError == null) {
                                String key = databaseReference.getKey();
                                StorageReference storageReference =
                                        FirebaseStorage.getInstance()
                                                .getReference(mFirebaseUser.getUid())
                                                .child(key)
                                                .child(imageUri.getLastPathSegment());

                                putImageInStorage(storageReference, imageUri, key);
                            } else {
                                Log.w(TAG, "Unable to write message to database.",
                                        databaseError.toException());
                            }
                        }
                    });
        }
    }

    public void sendMessage(String text) {
        if (text != null && text.length() > 0) {
            FriendlyMessage friendlyMessage = new FriendlyMessage(text, mUsername,
                    mPhotoUrl, null);
            mFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(friendlyMessage);
        }
    }

    private void putImageInStorage(StorageReference storageReference, Uri uri, final String key) {
        storageReference.putFile(uri).addOnCompleteListener(
                new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            FriendlyMessage friendlyMessage =
                                    new FriendlyMessage(null, mUsername, mPhotoUrl,
                                            task.getResult().getDownloadUrl().toString());
                            mFirebaseDatabaseReference.child(MESSAGES_CHILD).child(key)
                                    .setValue(friendlyMessage);
                        } else {
                            Log.w(TAG, "Image upload task was not successful.",
                                    task.getException());
                        }
                    }
                });
    }

    // Fetch the config to determine the allowed length of messages.
    public void fetchConfig() {
        long cacheExpiration = 3600; // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
        // server. This should not be used in release builds.
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Make the fetched config available via FirebaseRemoteConfig get<type> calls.
                        mFirebaseRemoteConfig.activateFetched();
                        mOnChangeConfigListener.onConfigChanged();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // There has been an error fetching the config
                        Log.w(TAG, "Error fetching config", e);
                        mOnChangeConfigListener.onConfigChanged();
                    }
                });
    }

    public int getFriendlyMsgLength() {
        return (int) mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH);
    }

    public interface OnChangeConfigListener {
        void onConfigChanged();
    }

    public interface OnMessagesUploadListener {
        void onMessagesUploaded();
    }
}
