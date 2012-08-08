package com.kmail.activity;

import com.kmail.R;
import com.kmail.mail.Flag;

/**
 * This enum represents filtering parameters used by {@link com.kmail.SearchAccount}.
 */
enum SearchModifier {
    FLAGGED(R.string.flagged_modifier, new Flag[]{Flag.FLAGGED}, null),
    UNREAD(R.string.unread_modifier, null, new Flag[]{Flag.SEEN});

    final int resId;
    final Flag[] requiredFlags;
    final Flag[] forbiddenFlags;

    SearchModifier(int nResId, Flag[] nRequiredFlags, Flag[] nForbiddenFlags) {
        resId = nResId;
        requiredFlags = nRequiredFlags;
        forbiddenFlags = nForbiddenFlags;
    }

}