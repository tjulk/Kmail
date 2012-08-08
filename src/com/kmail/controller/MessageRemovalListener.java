package com.kmail.controller;

import com.kmail.mail.Message;

public interface MessageRemovalListener {
    public void messageRemoved(Message message);
}
