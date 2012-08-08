package com.kmail;

import com.kmail.activity.K9Activity;
import com.kmail.helper.DateFormatter;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;

public class TranslationTest extends AndroidTestCase {
	public void testDateFormats() {
		forAllLanguages(new LanguageSpecific() {
			@Override
			public void runWithLanguage(Context context, String language) {
				Resources res = context.getResources();
				String dateFormatCommon = res.getString(R.string.date_format_common);
				try {
					DateFormatter.getDateFormat(mContext, dateFormatCommon);
				} catch (Exception e) {
					fail("Invalid date format string \"" + dateFormatCommon +
							"\" for language \"" + language + "\"");
				}
			}
		});
	}

	private void forAllLanguages(LanguageSpecific action) {
		Resources res = mContext.getResources();
		String[] languages = res.getStringArray(R.array.supported_languages);

		for (String lang : languages) {
			K9Activity.setLanguage(mContext, lang);

			action.runWithLanguage(mContext, lang);
		}
	}

	interface LanguageSpecific {
		void runWithLanguage(Context context, String language);
	}
}
