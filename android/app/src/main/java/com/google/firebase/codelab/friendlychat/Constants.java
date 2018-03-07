package com.google.firebase.codelab.friendlychat;

/**
 * Created by Sergey-PC on 02.03.2018.
 */

public interface Constants {

    String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
    String ANONYMOUS = "anonymous";

    String MESSAGE_URL = "http://friendlychat.firebase.google.com/message/";
    String MESSAGES_CHILD = "messages";
    String MESSAGE_SENT_EVENT = "message_sent";

    int DEFAULT_MSG_LENGTH_LIMIT = 10;

    //Preferences
    String INSTANCE_ID_TOKEN_RETRIEVED = "iid_token_retrieved";
    String FRIENDLY_MSG_LENGTH = "friendly_msg_length";
}
