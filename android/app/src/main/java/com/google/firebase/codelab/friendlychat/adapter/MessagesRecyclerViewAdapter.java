package com.google.firebase.codelab.friendlychat.adapter;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.Action;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseUserActions;
import com.google.firebase.appindexing.Indexable;
import com.google.firebase.appindexing.builders.Indexables;
import com.google.firebase.appindexing.builders.PersonBuilder;
import com.google.firebase.codelab.friendlychat.R;
import com.google.firebase.codelab.friendlychat.model.FriendlyMessage;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import de.hdodenhof.circleimageview.CircleImageView;

import static com.google.firebase.codelab.friendlychat.Constants.MESSAGE_URL;

public class MessagesRecyclerViewAdapter extends FirebaseRecyclerAdapter<FriendlyMessage, MessagesRecyclerViewAdapter.MessageViewHolder> {
    private static final String TAG = "MessagesRVAdapter";
    private String mUsername;
    /**
     * Initialize a {@link RecyclerView.Adapter} that listens to a Firebase query. See
     * {@link FirebaseRecyclerOptions} for configuration options.
     *
     * @param options
     */
    public MessagesRecyclerViewAdapter(String username, FirebaseRecyclerOptions<FriendlyMessage> options) {
        super(options);
        mUsername = username;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        return new MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false));
    }

    @Override
    protected void onBindViewHolder(final MessageViewHolder viewHolder,
                                    int position,
                                    FriendlyMessage friendlyMessage) {
        viewHolder.bind(friendlyMessage);
    }

    private Action getMessageViewAction(FriendlyMessage friendlyMessage) {
        return new Action.Builder(Action.Builder.VIEW_ACTION)
                .setObject(friendlyMessage.getName(), MESSAGE_URL.concat(friendlyMessage.getId()))
                .setMetadata(new Action.Metadata.Builder().setUpload(false))
                .build();
    }

    private Indexable getMessageIndexable(FriendlyMessage friendlyMessage) {
        PersonBuilder sender = Indexables.personBuilder()
                .setIsSelf(mUsername.equals(friendlyMessage.getName()))
                .setName(friendlyMessage.getName())
                .setUrl(MESSAGE_URL.concat(friendlyMessage.getId() + "/sender"));

        PersonBuilder recipient = Indexables.personBuilder()
                .setName(mUsername)
                .setUrl(MESSAGE_URL.concat(friendlyMessage.getId() + "/recipient"));

        return Indexables.messageBuilder()
                .setName(friendlyMessage.getText())
                .setUrl(MESSAGE_URL.concat(friendlyMessage.getId()))
                .setSender(sender)
                .setRecipient(recipient)
                .build();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageTextView;
        private ImageView messageImageView;
        private TextView messengerTextView;
        private CircleImageView messengerImageView;

        MessageViewHolder(View v) {
            super(v);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            messengerTextView = itemView.findViewById(R.id.messengerTextView);
            messengerImageView = itemView.findViewById(R.id.messengerImageView);
        }

        void bind(FriendlyMessage friendlyMessage){
            //todo     mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            if (friendlyMessage.getText() != null) {
                messageTextView.setText(friendlyMessage.getText());
                messageTextView.setVisibility(TextView.VISIBLE);
                messageImageView.setVisibility(ImageView.GONE);
            } else {
                String imageUrl = friendlyMessage.getImageUrl();
                if (imageUrl.startsWith("gs://")) {
                    StorageReference storageReference = FirebaseStorage.getInstance()
                            .getReferenceFromUrl(imageUrl);
                    storageReference.getDownloadUrl().addOnCompleteListener(
                            new OnCompleteListener<Uri>() {
                                @Override
                                public void onComplete(@NonNull Task<Uri> task) {
                                    if (task.isSuccessful()) {
                                        String downloadUrl = task.getResult().toString();
                                        Glide.with(messageImageView.getContext())
                                                .load(downloadUrl)
                                                .into(messageImageView);
                                    } else {
                                        Log.w(TAG, "Getting download url was not successful.",
                                                task.getException());
                                    }
                                }
                            });
                } else {
                    Glide.with(messageImageView.getContext())
                            .load(friendlyMessage.getImageUrl())
                            .into(messageImageView);
                }
                messageImageView.setVisibility(ImageView.VISIBLE);
                messageTextView.setVisibility(TextView.GONE);
            }

            messengerTextView.setText(friendlyMessage.getName());
            if (friendlyMessage.getPhotoUrl() == null) {
                messengerImageView.setImageDrawable(ContextCompat.getDrawable(messengerImageView.getContext(),
                        R.drawable.ic_account_circle_black_36dp));
            } else {
                Glide.with(messengerImageView.getContext())
                        .load(friendlyMessage.getPhotoUrl())
                        .into(messengerImageView);
            }

            if (friendlyMessage.getText() != null) {
                // write this message to the on-device index
                FirebaseAppIndex.getInstance().update(getMessageIndexable(friendlyMessage));
            }

            // log a view action on it
            FirebaseUserActions.getInstance().end(getMessageViewAction(friendlyMessage));
        }
    }
}
