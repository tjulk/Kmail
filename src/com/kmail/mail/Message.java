
package com.kmail.mail;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;

import android.util.Log;


import com.kmail.K9;
import com.kmail.activity.MessageReference;
import com.kmail.mail.filter.CountingOutputStream;
import com.kmail.mail.filter.EOLConvertingOutputStream;
import com.kmail.mail.store.UnavailableStorageException;


public abstract class Message implements Part, Body {
    private static final Flag[] EMPTY_FLAG_ARRAY = new Flag[0];

    private MessageReference mReference = null;

    public enum RecipientType {
        TO, CC, BCC,
    }

    protected String mUid;

    protected HashSet<Flag> mFlags = new HashSet<Flag>();

    protected Date mInternalDate;

    protected Folder mFolder;

    public boolean olderThan(Date earliestDate) {
        if (earliestDate == null) {
            return false;
        }
        Date myDate = getSentDate();
        if (myDate == null) {
            myDate = getInternalDate();
        }
        if (myDate != null) {
            return myDate.before(earliestDate);
        }
        return false;
    }
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Message)) {
            return false;
        }
        Message other = (Message)o;
        return (mFolder.getName().equals(other.getFolder().getName())
                && mFolder.getAccount().getUuid().equals(other.getFolder().getAccount().getUuid())
                && mUid.equals(other.getUid()));
    }

    @Override
    public int hashCode() {
        final int MULTIPLIER = 31;

        int result = 1;
        result = MULTIPLIER * result + mFolder.getName().hashCode();
        result = MULTIPLIER * result + mFolder.getAccount().getUuid().hashCode();
        result = MULTIPLIER * result + mUid.hashCode();
        return result;
    }

    public String getUid() {
        return mUid;
    }

    public void setUid(String uid) {
        mReference = null;
        this.mUid = uid;
    }

    public Folder getFolder() {
        return mFolder;
    }

    public abstract String getSubject();

    public abstract void setSubject(String subject) throws MessagingException;

    public Date getInternalDate() {
        return mInternalDate;
    }

    public void setInternalDate(Date internalDate) {
        this.mInternalDate = internalDate;
    }

    public abstract Date getSentDate();

    public abstract void setSentDate(Date sentDate) throws MessagingException;

    public abstract Address[] getRecipients(RecipientType type) throws MessagingException;

    public abstract void setRecipients(RecipientType type, Address[] addresses)
    throws MessagingException;

    public void setRecipient(RecipientType type, Address address) throws MessagingException {
        setRecipients(type, new Address[] {
                          address
                      });
    }

    public abstract Address[] getFrom();

    public abstract void setFrom(Address from) throws MessagingException;

    public abstract Address[] getReplyTo();

    public abstract void setReplyTo(Address[] from) throws MessagingException;

    public abstract String getMessageId() throws MessagingException;

    public abstract void setInReplyTo(String inReplyTo) throws MessagingException;

    public abstract String[] getReferences() throws MessagingException;

    public abstract void setReferences(String references) throws MessagingException;

    public abstract Body getBody();

    public abstract String getContentType() throws MessagingException;

    public abstract void addHeader(String name, String value) throws MessagingException;

    public abstract void setHeader(String name, String value) throws MessagingException;

    public abstract String[] getHeader(String name) throws MessagingException;

    public abstract Set<String> getHeaderNames() throws UnavailableStorageException;

    public abstract void removeHeader(String name) throws MessagingException;

    public abstract void setBody(Body body) throws MessagingException;

    public boolean isMimeType(String mimeType) throws MessagingException {
        return getContentType().startsWith(mimeType);
    }

    public void delete(String trashFolderName) throws MessagingException {}

    /*
     * TODO Refactor Flags at some point to be able to store user defined flags.
     */
    public Flag[] getFlags() {
        return mFlags.toArray(EMPTY_FLAG_ARRAY);
    }

    /**
     * @param flag
     *            Flag to set. Never <code>null</code>.
     * @param set
     *            If <code>true</code>, the flag is added. If <code>false</code>
     *            , the flag is removed.
     * @throws MessagingException
     */
    public void setFlag(Flag flag, boolean set) throws MessagingException {
        if (set) {
            mFlags.add(flag);
        } else {
            mFlags.remove(flag);
        }
    }

    /**
     * This method calls setFlag(Flag, boolean)
     * @param flags
     * @param set
     */
    public void setFlags(Flag[] flags, boolean set) throws MessagingException {
        for (Flag flag : flags) {
            setFlag(flag, set);
        }
    }

    public boolean isSet(Flag flag) {
        return mFlags.contains(flag);
    }


    public void destroy() throws MessagingException {}

    public abstract void saveChanges() throws MessagingException;

    public abstract void setEncoding(String encoding) throws UnavailableStorageException;

    public abstract void setCharset(String charset) throws MessagingException;

    public MessageReference makeMessageReference() {
        if (mReference == null) {
            mReference = new MessageReference();
            mReference.accountUuid = getFolder().getAccount().getUuid();
            mReference.folderName = getFolder().getName();
            mReference.uid = mUid;
        }
        return mReference;
    }

    public boolean equalsReference(MessageReference ref) {
        MessageReference tmpReference = makeMessageReference();
        return tmpReference.equals(ref);
    }

    public long calculateSize() {
        try {

            CountingOutputStream out = new CountingOutputStream();
            EOLConvertingOutputStream eolOut = new EOLConvertingOutputStream(out);
            writeTo(eolOut);
            eolOut.flush();
            return out.getCount();
        } catch (IOException e) {
            Log.e(K9.LOG_TAG, "Failed to calculate a message size", e);
        } catch (MessagingException e) {
            Log.e(K9.LOG_TAG, "Failed to calculate a message size", e);
        }
        return 0;
    }

    /**
     * Copy the contents of this object into another {@code Message} object.
     *
     * @param destination
     *         The {@code Message} object to receive the contents of this instance.
     */
    protected void copy(Message destination) {
        destination.mUid = mUid;
        destination.mInternalDate = mInternalDate;
        destination.mFolder = mFolder;
        destination.mReference = mReference;

        // mFlags contents can change during the object lifetime, so copy the Set
        destination.mFlags = new HashSet<Flag>(mFlags);
    }

    /**
     * Creates a new {@code Message} object with the same content as this object.
     *
     * <p>
     * <strong>Note:</strong>
     * This method was introduced as a hack to prevent {@code ConcurrentModificationException}s. It
     * shouldn't be used unless absolutely necessary. See the comment in
     * {@link com.kmail.activity.MessageView.Listener#loadMessageForViewHeadersAvailable(com.kmail.Account, String, String, Message)}
     * for more information.
     * </p>
     */
    public abstract Message clone();
}
